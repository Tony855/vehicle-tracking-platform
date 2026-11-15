package com.tony855.vehicletracking.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiInfoController.class)
class ApiInfoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsVersionedApiInformation() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("vehicle-tracking-api"))
            .andExpect(jsonPath("$.apiVersion").value("v1"))
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
