package com.example.gitbot.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class TestController {

        @GetMapping("/test/illegal-argument")
        public void throwIllegalArgument() {
            throw new IllegalArgumentException("Invalid user ID format");
        }

        @GetMapping("/test/generic-exception")
        public void throwGeneric() {
            throw new RuntimeException("Sensitive database connection details or provider leak");
        }

        @GetMapping("/test/not-found")
        public void throwNotFound() {
            throw new NotFoundException("User not found");
        }

        @GetMapping("/test/bad-request")
        public void throwBadRequest() {
            throw new BadRequestException("Malformed input");
        }

        @GetMapping("/test/unauthorized")
        public void throwUnauthorized() {
            throw new UnauthorizedException("Session expired");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handleIllegalArgumentException_returns400_withClientSafeMessage() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid user ID format"));
    }

    @Test
    void handleGenericException_returns500_withSafeMessage_andHidesInternalDetails() throws Exception {
        mockMvc.perform(get("/test/generic-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred."))
                .andExpect(jsonPath("$.message").value(not("Sensitive database connection details or provider leak")));
    }

    @Test
    void handleNotFoundException_returns404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void handleBadRequestException_returns400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed input"));
    }

    @Test
    void handleUnauthorizedException_returns401() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Session expired"));
    }
}
