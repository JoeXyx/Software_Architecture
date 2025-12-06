package org.example.ems.ws;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;

@WebService
public interface EmailService {

    @WebMethod
    String sendEmail(
            @WebParam(name = "toAddress") String toAddress,
            @WebParam(name="name")String name,
            @WebParam(name = "payload") String payload);

    @WebMethod
    String sendEmailBatch(
            @WebParam(name = "toAddresses") String[] toAddresses,
            @WebParam(name = "payload") String payload);

    @WebMethod
    String validateEmailAddress(
            @WebParam(name = "emailAddress") String emailAddress);
}