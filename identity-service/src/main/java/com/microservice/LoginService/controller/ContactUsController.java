package com.microservice.LoginService.controller;

import com.microservice.LoginService.dto.ContactUsRequest;
import com.microservice.LoginService.entity.ContactEnquiry;
import com.microservice.LoginService.repository.ContactEnquiryRepository;
import com.microservice.LoginService.service.EmailService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/contact-us")
public class ContactUsController {

    @Autowired
    private ContactEnquiryRepository contactEnquiryRepository;

    @Autowired
    private EmailService emailService;

    @PostMapping
    public ResponseEntity<?> submitEnquiry(@Valid @RequestBody ContactUsRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                errors.put(error.getField(), error.getDefaultMessage());
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Validation Failed");
            response.put("errors", errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            // Save enquiry to database
            ContactEnquiry enquiry = ContactEnquiry.builder()
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .restaurantName(request.getRestaurantName())
                    .message(request.getMessage())
                    .build();
            contactEnquiryRepository.save(enquiry);

            // Format submitted date (e.g. 17 July 2026)
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
            String submittedOn = LocalDateTime.now().format(formatter);

            // Construct email body formatted exactly as requested
            String phoneVal = request.getPhoneNumber() != null ? request.getPhoneNumber() : "";
            String restaurantVal = request.getRestaurantName() != null ? request.getRestaurantName() : "";

            String emailBodyText = String.format(
                    "A new enquiry has been received.\n\n" +
                    "-------------------------------------\n\n" +
                    "Full Name:\n" +
                    "%s\n\n" +
                    "Email:\n" +
                    "%s\n\n" +
                    "Phone Number:\n" +
                    "%s\n\n" +
                    "Restaurant Name:\n" +
                    "%s\n\n" +
                    "Message:\n\n" +
                    "%s\n\n" +
                    "-------------------------------------\n\n" +
                    "Submitted On:\n" +
                    "%s",
                    request.getFullName(),
                    request.getEmail(),
                    phoneVal,
                    restaurantVal,
                    request.getMessage(),
                    submittedOn
            );

            // Wrap in styled HTML preserving whitespace and linebreaks
            String emailHtmlBody = "<div style=\"font-family: Arial, sans-serif; white-space: pre-wrap; line-height: 1.6; color: #333;\">" 
                    + emailBodyText 
                    + "</div>";

            // Send notification to support@qwikue.in
            emailService.sendContactUsEmail("support@qwikue.in", emailHtmlBody);

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Your enquiry has been submitted successfully.");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to submit contact enquiry", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Something went wrong. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
