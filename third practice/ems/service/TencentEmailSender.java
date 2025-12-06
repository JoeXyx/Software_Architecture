package org.example.ems.service;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;

import com.tencentcloudapi.ses.v20201002.models.Template;
import org.example.ems.util.TencentMailConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
public class TencentEmailSender {

    @Autowired
    private TencentMailConfig config;

    private SesClient buildClient() {
        Credential cred = new Credential(config.getSecretId(), config.getSecretKey());

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("ses.tencentcloudapi.com");

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        return new SesClient(cred, "ap-guangzhou", clientProfile);
    }

    public boolean sendEmail_template(String to ,String subject,String content) throws Exception{

        SesClient client = buildClient();

//        创建邮件请求
        SendEmailRequest request = new SendEmailRequest();
        request.setFromEmailAddress(config.getTencent_mail_sender());//发信地址

//        收件人
        request.setDestination(new String[]{to});
        System.out.println(content);

//        使用模板
        Template template=new Template();
        template.setTemplateID(config.getTemplateId());//模板ID,在腾讯云SES创建
        template.setTemplateData(content);
        request.setTemplate(template);
        request.setSubject(subject);

//        发送
        client.SendEmail(request);
        return true;
    }
}
