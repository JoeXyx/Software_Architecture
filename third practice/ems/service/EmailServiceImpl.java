package org.example.ems.service;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import com.tencentcloudapi.ses.v20201002.models.Template;
import jakarta.jws.WebService;
import org.example.ems.ws.EmailService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

@Service
@WebService(
        endpointInterface = "org.example.ems.ws.EmailService",
        serviceName = "EmailServiceImplService",
        portName = "EmailServiceImplPort",
        targetNamespace = "http://ws.ems.example.org/"
)
public class EmailServiceImpl implements EmailService {

    private  final String SECRET_ID ;
    private  final String SECRET_KEY ;
    private  final String REGION ;
    private  final String FROM_EMAIL;
    private  final Long TEMPLATE_ID ;

    // 模板变量 JSON 格式：{"name":"张三","content":"具体通知内容"}
    private static final String TEMPLATE_DATA_FORMAT = "{\"name\":\"%s\",\"content\":\"%s\"}";

    private final SesClient client;

    public EmailServiceImpl() {
        // 读取 application.properties
        Properties prop = loadProperties();

        SECRET_ID = prop.getProperty("secretId");
        SECRET_KEY = prop.getProperty("secretKey");
        REGION = prop.getProperty("tencent.ses.region", "ap-guangzhou");
        FROM_EMAIL = prop.getProperty("tencent.mail.sender");
        TEMPLATE_ID = Long.parseLong(prop.getProperty("templateId"));

        // 校验必填配置
        if (SECRET_ID == null || SECRET_KEY == null || FROM_EMAIL == null) {
            throw new RuntimeException("腾讯云 SES 配置不完整，请检查 application.properties 文件");
        }
        Credential cred = new Credential(SECRET_ID, SECRET_KEY);
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("ses.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        client = new SesClient(cred, REGION, clientProfile);
    }

    // 加载 properties 文件的方法
    private Properties loadProperties() {
        Properties prop = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("无法找到 application.properties 文件，请确保放在 src/main/resources 目录下");
            }
            prop.load(input);
        } catch (IOException e) {
            throw new RuntimeException("读取 application.properties 失败", e);
        }
        return prop;
    }

    @Override
    public String sendEmail(String toAddress,String name, String payload) {
        // 默认姓名为空时用“用户”
        if(name==null){name = "";}
        System.out.println(name);
        System.out.println(payload);

        SendEmailRequest req = new SendEmailRequest();
        req.setFromEmailAddress(FROM_EMAIL);
        req.setDestination(new String[]{toAddress});


        Template template = new Template();
        template.setTemplateID(TEMPLATE_ID);
        // 填充变量：name 使用默认“用户”，content 使用 payload
        template.setTemplateData(String.format(TEMPLATE_DATA_FORMAT, name, escapeJson(payload)));
        req.setSubject("通知");
        req.setTemplate(template);

        try {
            SendEmailResponse resp = client.SendEmail(req);
            return resp.getMessageId() != null ? "Y" : "N";
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
            return "N";
        }
    }

    @Override
    public String sendEmailBatch(String[] toAddresses, String payload) {
        if (toAddresses == null || toAddresses.length == 0) {
            return "N"; // 无收件人，直接返回失败
        }

        String name = ""; // 统一默认姓名

        // 构建模板数据（与单发保持一致）
        String templateData = String.format(TEMPLATE_DATA_FORMAT, name, escapeJson(payload));

        // 记录成功和失败的数量，用于返回更详细的结果（这里简化为整体 Y/N）
        int successCount = 0;
        int totalCount = toAddresses.length;

        for (String to : toAddresses) {
            if (to == null || to.trim().isEmpty()) {
                continue; // 跳过空地址
            }

            SendEmailRequest req = new SendEmailRequest();
            req.setFromEmailAddress(FROM_EMAIL);
            req.setDestination(new String[]{to.trim()});

            Template template = new Template();
            template.setTemplateID(TEMPLATE_ID);
            template.setTemplateData(templateData); // 所有收件人使用相同模板数据
            req.setTemplate(template);
            req.setSubject("通知"); // 主题保持一致

            try {
                SendEmailResponse resp = client.SendEmail(req);
                if (resp.getMessageId() != null && !resp.getMessageId().isEmpty()) {
                    successCount++;
                    System.out.println("批量发送成功 -> " + to);
                } else {
                    System.out.println("批量发送失败（无MessageId） -> " + to);
                }
            } catch (TencentCloudSDKException e) {
                e.printStackTrace();
                System.out.println("批量发送异常 -> " + to + ": " + e.getMessage());
                // 单个失败不中断，继续发送其他邮件
            }
        }

        // 判断整体是否成功：全部成功返回 "Y"，部分或全部失败返回 "N"
        // 你也可以返回更详细的结果，如 "successCount/totalCount"
        boolean allSuccess = (successCount == totalCount);
        System.out.println("批量发送完成：成功 " + successCount + "/" + totalCount);
        return allSuccess ? "Y" : "N";
    }

    // 正则验证邮箱（符合作业要求）
    private static final String EMAIL_REGEX =
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private static final Pattern PATTERN = Pattern.compile(EMAIL_REGEX);

    @Override
    public String validateEmailAddress(String emailAddress) {
        if (emailAddress == null || emailAddress.trim().isEmpty()) {
            return "N";
        }
        return PATTERN.matcher(emailAddress.trim()).matches() ? "Y" : "N";
    }

    // 简单转义 JSON 中的双引号，防止 payload 包含 " 导致 JSON 格式错误
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}