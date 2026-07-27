package dio.budgeting.infrastructure.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.net.URI;

/**
 * Registers the {@code ResponseErrorHandler} used by every OpenAI client
 * (chat, transcription, speech): {@code OpenAiChatAutoConfiguration} and its
 * siblings look up this bean via {@code ObjectProvider}, so this definition
 * takes precedence over the default one auto-configured by
 * {@code SpringAiRetryAutoConfiguration} ({@code @ConditionalOnMissingBean}).
 *
 * <p>Unlike the framework default, this handler never reads or logs the
 * response body (which may echo back parts of an invalid API key), and it
 * exposes the real HTTP status as a typed field on {@link OpenAiServerException}/
 * {@link OpenAiClientException} instead of embedding it in the exception
 * message, so callers can classify failures by status without parsing text.
 */
@Configuration
public class OpenAiHttpErrorHandlerConfig {

    @Bean
    public ResponseErrorHandler responseErrorHandler() {
        return new ResponseErrorHandler() {

            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return response.getStatusCode().isError();
            }

            @Override
            public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
                if (!response.getStatusCode().isError()) {
                    return;
                }
                int statusCode = response.getStatusCode().value();
                if (response.getStatusCode().is5xxServerError()) {
                    throw new OpenAiServerException(statusCode);
                }
                throw new OpenAiClientException(statusCode);
            }
        };
    }
}
