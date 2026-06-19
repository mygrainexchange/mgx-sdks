/* Hand-written overlay — not generated. */
using System;
using System.Collections.Generic;
using System.Text.Json;
using MyGrainExchange.Api.Client;

namespace MyGrainExchange.Api.Overlay
{
    /// <summary>A single field-level validation error.</summary>
    public sealed class MgxFieldError
    {
        public string Field { get; }
        public string Message { get; }

        public MgxFieldError(string field, string message)
        {
            Field = field;
            Message = message;
        }
    }

    /// <summary>
    /// Typed error mapped from the API's <c>{ "error": { status, code, message, errors[] } }</c>
    /// envelope. Thrown by every <see cref="MgxClient"/> method instead of the generated
    /// <see cref="ApiException"/>.
    /// </summary>
    public sealed class MgxApiError : Exception
    {
        /// <summary>HTTP status code (or the status echoed in the error envelope).</summary>
        public int Status { get; }

        /// <summary>Machine-readable error code (e.g. <c>validation_failed</c>).</summary>
        public string Code { get; }

        /// <summary>Field-level validation errors, when present.</summary>
        public IReadOnlyList<MgxFieldError> FieldErrors { get; }

        public MgxApiError(int status, string code, string message, IReadOnlyList<MgxFieldError> fieldErrors = null)
            : base(message)
        {
            Status = status;
            Code = code;
            FieldErrors = fieldErrors ?? Array.Empty<MgxFieldError>();
        }

        /// <summary>
        /// Builds an <see cref="MgxApiError"/> from the generated <see cref="ApiException"/>,
        /// parsing its <see cref="ApiException.ErrorContent"/> (the raw response body) as the
        /// MGX error envelope. Falls back to the HTTP status when the body is missing or not JSON.
        /// </summary>
        public static MgxApiError FromApiException(ApiException ex)
        {
            int status = ex.ErrorCode;
            string code = "error";
            string message = string.IsNullOrEmpty(ex.Message) ? "Request failed" : ex.Message;
            var fieldErrors = new List<MgxFieldError>();

            var body = ex.ErrorContent as string ?? ex.ErrorContent?.ToString();
            if (!string.IsNullOrWhiteSpace(body))
            {
                try
                {
                    using var doc = JsonDocument.Parse(body);
                    if (doc.RootElement.ValueKind == JsonValueKind.Object &&
                        doc.RootElement.TryGetProperty("error", out var error) &&
                        error.ValueKind == JsonValueKind.Object)
                    {
                        if (error.TryGetProperty("status", out var s) && s.ValueKind == JsonValueKind.Number)
                            status = s.GetInt32();
                        if (error.TryGetProperty("code", out var c) && c.ValueKind == JsonValueKind.String)
                            code = c.GetString();
                        if (error.TryGetProperty("message", out var m) && m.ValueKind == JsonValueKind.String)
                            message = m.GetString();
                        if (error.TryGetProperty("errors", out var errs) && errs.ValueKind == JsonValueKind.Array)
                        {
                            foreach (var item in errs.EnumerateArray())
                            {
                                if (item.ValueKind != JsonValueKind.Object) continue;
                                var field = item.TryGetProperty("field", out var f) && f.ValueKind == JsonValueKind.String
                                    ? f.GetString() : string.Empty;
                                var fm = item.TryGetProperty("message", out var im) && im.ValueKind == JsonValueKind.String
                                    ? im.GetString() : string.Empty;
                                fieldErrors.Add(new MgxFieldError(field, fm));
                            }
                        }
                    }
                }
                catch (JsonException)
                {
                    /* non-JSON error body — keep the HTTP status/message. */
                }
            }

            return new MgxApiError(status, code, message, fieldErrors);
        }
    }
}
