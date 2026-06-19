<?php

/* Hand-written overlay — not generated. */

declare(strict_types=1);

namespace MyGrainExchange\Mgx\Overlay;

use MyGrainExchange\Mgx\ApiException;

/**
 * Typed error mapped from the API's
 * `{ "error": { status, code, message, errors[] } }` envelope.
 *
 * Field-level validation errors are exposed as a list of
 * `['field' => string, 'message' => string]` arrays via {@see fieldErrors()}.
 */
class MgxApiError extends \Exception
{
    /** @var int HTTP status code. */
    private int $status;

    /** @var string Machine-readable error code (e.g. "validation_error"). */
    private string $errorCode;

    /** @var list<array{field: string, message: string}> */
    private array $fieldErrors;

    /**
     * @param list<array{field: string, message: string}> $fieldErrors
     */
    public function __construct(
        int $status,
        string $code,
        string $message,
        array $fieldErrors = [],
        ?\Throwable $previous = null,
    ) {
        parent::__construct($message, $status, $previous);
        $this->status = $status;
        $this->errorCode = $code;
        $this->fieldErrors = $fieldErrors;
    }

    /** HTTP status code of the failed response. */
    public function status(): int
    {
        return $this->status;
    }

    /** Machine-readable error code from the API envelope. */
    public function code(): string
    {
        return $this->errorCode;
    }

    /** @return list<array{field: string, message: string}> */
    public function fieldErrors(): array
    {
        return $this->fieldErrors;
    }

    /**
     * Build an {@see MgxApiError} from a generated {@see ApiException}, parsing the
     * `{ error: { status, code, message, errors[] } }` envelope when present.
     */
    public static function fromApiException(ApiException $e): self
    {
        $status = (int) $e->getCode();
        if ($status === 0) {
            $status = 500;
        }
        $code = 'error';
        $message = $e->getMessage() !== '' ? $e->getMessage() : 'Request failed';
        $fieldErrors = [];

        $body = self::decodeBody($e->getResponseBody());
        if (is_array($body) && isset($body['error']) && is_array($body['error'])) {
            $error = $body['error'];
            if (isset($error['status'])) {
                $status = (int) $error['status'];
            }
            if (isset($error['code']) && is_string($error['code'])) {
                $code = $error['code'];
            }
            if (isset($error['message']) && is_string($error['message'])) {
                $message = $error['message'];
            }
            if (isset($error['errors']) && is_array($error['errors'])) {
                foreach ($error['errors'] as $fieldError) {
                    if (!is_array($fieldError)) {
                        continue;
                    }
                    $fieldErrors[] = [
                        'field' => (string) ($fieldError['field'] ?? ''),
                        'message' => (string) ($fieldError['message'] ?? ''),
                    ];
                }
            }
        }

        return new self($status, $code, $message, $fieldErrors, $e);
    }

    /**
     * Normalize the generated exception's response body (which may be a JSON
     * string, an \stdClass, or already an array) into an associative array.
     *
     * @param mixed $body
     * @return array<string, mixed>|null
     */
    private static function decodeBody($body): ?array
    {
        if (is_string($body)) {
            $decoded = json_decode($body, true);

            return is_array($decoded) ? $decoded : null;
        }
        if ($body instanceof \stdClass) {
            $decoded = json_decode((string) json_encode($body), true);

            return is_array($decoded) ? $decoded : null;
        }
        if (is_array($body)) {
            return $body;
        }

        return null;
    }
}
