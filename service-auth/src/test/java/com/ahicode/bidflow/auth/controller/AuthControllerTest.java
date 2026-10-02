package com.ahicode.bidflow.auth.controller;

import com.ahicode.bidflow.auth.TestSecurityConfiguration;
import com.ahicode.bidflow.auth.controllers.AuthController;
import com.ahicode.bidflow.auth.dtos.AuthResponse;
import com.ahicode.bidflow.auth.dtos.LoginRequest;
import com.ahicode.bidflow.auth.dtos.RegisterRequest;
import com.ahicode.bidflow.auth.dtos.UserProfile;
import com.ahicode.bidflow.auth.enums.UserRole;
import com.ahicode.bidflow.auth.enums.UserStatus;
import com.ahicode.bidflow.auth.exceptions.EmailAlreadyExistsException;
import com.ahicode.bidflow.auth.exceptions.InvalidCredentialException;
import com.ahicode.bidflow.auth.exceptions.UserBlockedException;
import com.ahicode.bidflow.auth.services.AuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({TestSecurityConfiguration.class})
public class AuthControllerTest {

    @MockitoBean private AuthService authService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;

    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile(
                userId,
                "test@mail.com",
                "Taylor",
                "Durden",
                UserRole.ROLE_USER,
                UserStatus.ACTIVE
        );

        authResponse = new AuthResponse(
                "safe.refresh.token",
                "safe.access.token",
                "Bearer",
                900,
                profile
        );
    }

    @Nested
    class Register {
        private final RegisterRequest request = new RegisterRequest(
                "test@mail.com",
                "Taylor",
                "Durden",
                "1234"
        );

        @Test
        void shouldReturnCreated_WhenRegistrationIsSuccessful() throws Exception {
            given(authService.register(request)).willReturn(authResponse);

            ResultActions resultActions = performRegisterRequest(request);

            resultActions.andExpect(status().isCreated());
            assertAuthResponse(resultActions);

            verify(authService).register(request);
        }

        @Test
        void shouldReturnBadRequest_WhenRequestDataInvalid() throws Exception {
            RegisterRequest invalidRegisterRequest = request.withEmail("not-validemail.com");

            ResultActions resultActions = performRegisterRequest(invalidRegisterRequest);

            resultActions.andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnConflict_WhenEmailIsAlreadyExists() throws Exception {
            given(authService.register(request)).willThrow(EmailAlreadyExistsException.class);

            ResultActions resultActions = performRegisterRequest(request);

            resultActions.andExpect(status().isConflict());
        }

        private ResultActions performRegisterRequest(RegisterRequest request) throws Exception {
            return mockMvc.perform(
                    post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request))
            );
        }
    }

    @Nested
    class Login {
        private final LoginRequest request = new LoginRequest(
                "test@mail.com",
                "1234"
        );

        @Test
        void shouldReturnOk_WhenLoginIsSuccessful() throws Exception {
            given(authService.login(request)).willReturn(authResponse);

            ResultActions resultActions = performLoginRequest(request);

            resultActions.andExpect(status().isOk());
            assertAuthResponse(resultActions);

            verify(authService).login(request);
        }

        @Test
        void shouldReturnBadRequest_WhenRequestDataInvalid() throws Exception {
            LoginRequest invalidRequest = request.withEmail("invalidemail.com");
            
            ResultActions resultActions = performLoginRequest(invalidRequest);
            
            resultActions.andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnUnauthorized_WhenEmailOrPasswordWrong() throws Exception {
            given(authService.login(request)).willThrow(InvalidCredentialException.class);
            
            ResultActions resultActions = performLoginRequest(request);
            
            resultActions.andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturnForbidden_WhenUserBlockedOrSuspended() throws Exception {
            given(authService.login(request)).willThrow(UserBlockedException.class);

            ResultActions resultActions = performLoginRequest(request);

            resultActions.andExpect(status().isForbidden());
        }

        private ResultActions performLoginRequest(LoginRequest request) throws Exception {
            return mockMvc.perform(
                    post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request))
            );
        }
    }

    private void assertAuthResponse(ResultActions resultActions) throws Exception {
        resultActions.andExpect(jsonPath("$.refreshToken").value("safe.refresh.token"))
                .andExpect(jsonPath("$.accessToken").value("safe.access.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").exists())
                .andExpect(jsonPath("$.userInfo").exists());
    }

    private String toJson(Object object) throws JsonProcessingException {
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper.writeValueAsString(object);
    }
}
