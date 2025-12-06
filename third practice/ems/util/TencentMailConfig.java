package org.example.ems.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TencentMailConfig {
    @Value("${secretId}")
    private String secretId;

    @Value("${secretKey}")
    private String secretKey;
    @Value("${SMTP.secretKey}")
    private String SMTP_secretKey;
    @Value("${tencent.mail.sender}")
    private String tencent_mail_sender;
    @Value("${tencent.mail.domain}")
    private String tencent_mail_domain;
    @Value("${templateId}")
    private long templateId;

    public long getTemplateId() {
        return templateId;
    }

    public String getSecretId() {
        return secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getSMTP_secretKey() {
        return SMTP_secretKey;
    }

    public String getTencent_mail_sender() {
        return tencent_mail_sender;
    }

    public String getTencent_mail_domain() {
        return tencent_mail_domain;
    }
}
