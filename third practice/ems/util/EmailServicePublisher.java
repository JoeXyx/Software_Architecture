package org.example.ems.util;

import jakarta.annotation.PostConstruct;
import jakarta.xml.ws.Endpoint;
import org.example.ems.service.EmailServiceImpl;
import org.springframework.stereotype.Component;

@Component
public class EmailServicePublisher {
    private final EmailServiceImpl emailService;

    public EmailServicePublisher(EmailServiceImpl emailService) {
        this.emailService = emailService;
    }

    @PostConstruct
    public void publish() {
        String address = "http://localhost:9999/ws/email";
        Endpoint.publish(address, emailService);
        System.out.println("SOAP Email Service 已发布！");
        System.out.println("WSDL 地址: " + address + "?wsdl");
    }
}
