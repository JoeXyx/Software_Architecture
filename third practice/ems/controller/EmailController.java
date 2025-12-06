package org.example.ems.controller;


import org.example.ems.service.TencentEmailSender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    @Autowired
    private TencentEmailSender tencentEmailSender;

    @PostMapping("/send")
    public String send(@RequestBody EmailRequest request)throws Exception{
        Map<String, String> map = new HashMap<>();
        map.put("content", request.getContent());
        map.put("name", request.getName());
        String jsonContent = new ObjectMapper().writeValueAsString(map);
        boolean result = tencentEmailSender.sendEmail_template(request.getTo(),request.getSubject(),jsonContent);
        return result ? "Y" : "N";
    }

    public static class EmailRequest{
        private String to;
        private String content;
        private String subject;
        private String name;

        public String getName() {
            return name;
        }

        public String getSubject() {
            return subject;
        }

        public String getTo() {
            return to;
        }

        public String getContent() {
            return content;
        }
    }

    @PostMapping("/sendEmail")
    public String send(@RequestBody String soapRequest) {
        try {
            URL url = new URL("http://localhost:9999/ws/email");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");

            conn.getOutputStream().write(soapRequest.getBytes());

            InputStream in = conn.getInputStream();
            return new String(in.readAllBytes());
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

}
