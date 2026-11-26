package com.reverse.nsu.config;

import com.reverse.nsu.controller.ClubProjectController;
import com.reverse.nsu.controller.OfficerController;
import com.reverse.nsu.service.ClubProjectService;
import com.reverse.nsu.service.EmailService;
import com.reverse.nsu.service.OfficerService;
import com.reverse.nsu.service.RoleCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = WebConfigCorsTest.MvcConfig.class)
class WebConfigCorsTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    @Import({WebConfig.class, ClubProjectController.class, OfficerController.class})
    static class MvcConfig {
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @MockitoBean
    private JwtInterceptor jwtInterceptor;

    @MockitoBean
    private ClubProjectService clubProjectService;

    @MockitoBean
    private OfficerService officerService;

    @MockitoBean
    private RoleCheckService roleCheckService;

    @MockitoBean
    private EmailService emailService;

    @Test
    void newWebsiteOriginsAreAllowedOnPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/club-project").header("Origin", "https://nsu-reverse.website"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://nsu-reverse.website"));

        mockMvc.perform(get("/api/officer").header("Origin", "https://www.nsu-reverse.website"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.nsu-reverse.website"));
    }

    @Test
    void existingWebsiteAndLocalhostOriginsRemainAllowed() throws Exception {
        for (String origin : new String[] {
                "https://nsu-reverse.site", "https://www.nsu-reverse.site",
                "http://localhost:3000", "http://localhost:5173"}) {
            mockMvc.perform(get("/api/club-project").header("Origin", origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    void preflightAllowsConfiguredWebsiteOrigin() throws Exception {
        mockMvc.perform(options("/api/club-project")
                        .header("Origin", "https://nsu-reverse.website")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://nsu-reverse.website"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void unlistedOriginIsRejected() throws Exception {
        mockMvc.perform(get("/api/club-project").header("Origin", "https://external.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsDoesNotBypassControllerAdminChecks() throws Exception {
        mockMvc.perform(post("/api/club-project")
                        .header("Origin", "https://nsu-reverse.website")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/officer")
                        .header("Origin", "https://nsu-reverse.website")
                        .requestAttr("userId", "member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
