package dasniko.keycloak.provider.email.aws;

import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.services.ServicesLogger;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class AwsSesEmailSenderProvider implements EmailSenderProvider {

    private final SesClient ses;

    AwsSesEmailSenderProvider(SesClient ses) {
        this.ses = ses;
    }

    @Override
    public void send(Map<String, String> config, String address, String subject, String textBody, String htmlBody) throws EmailException {
        boolean auth = "true".equals(config.get("auth"));
        String from = config.get("from");
        String fromDisplayName = config.get("fromDisplayName");
        String replyTo = config.get("replyTo");
        String replyToDisplayName = config.get("replyToDisplayName");

        try {
            if (from == null || from.isEmpty()) {
                throw new Exception("Missing 'from' email address.");
            }

            SendEmailRequest.Builder sendEmailRequest = SendEmailRequest.builder()
                .destination(
                    Destination.builder().toAddresses(address).build()
                )
                .message(Message.builder()
                    .subject(Content.builder().charset(StandardCharsets.UTF_8.toString()).data(subject).build())
                    .body(Body.builder()
                        .html(Content.builder().charset(StandardCharsets.UTF_8.toString()).data(htmlBody).build())
                        .text(Content.builder().charset(StandardCharsets.UTF_8.toString()).data(textBody).build())
                        .build()
                    )
                    .build()
                )
                .source(toInternetAddress(from, fromDisplayName).toString());

            if (replyTo != null && !replyTo.isEmpty()) {
                sendEmailRequest.replyToAddresses(
                    Collections.singletonList(toInternetAddress(replyTo, replyToDisplayName).toString()));
            }

            if (auth) {
                String user = config.get("user");
                String password = config.get("password");
    
                SesClient ses1 = SesClient.builder()
                    .region(ses.serviceClientConfiguration().region())
                    .credentialsProvider(() -> AwsBasicCredentials.create(user, password))
                    .build();
                ses1.sendEmail(sendEmailRequest.build());
                return;
            }

            ses.sendEmail(sendEmailRequest.build());
        } catch (Exception e) {
            ServicesLogger.LOGGER.failedToSendEmail(e);
            throw new EmailException(e);
        }
    }

    private InternetAddress toInternetAddress(String email, String displayName) throws Exception {
        if (email == null || "".equals(email.trim())) {
            throw new EmailException("Please provide a valid address", null);
        }
        if (displayName == null || "".equals(displayName.trim())) {
            return new InternetAddress(email);
        }
        return new InternetAddress(email, displayName, StandardCharsets.UTF_8.toString());
    }

    @Override
    public void close() {
    }
}
