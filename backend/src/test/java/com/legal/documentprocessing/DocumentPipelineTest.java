package com.legal.documentprocessing;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentPipelineTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadsAndProcessesSyntheticContractWithoutKafka() throws Exception {
        String contract = """
                Demo Supply Agreement

                On 04/15/2025, the parties signed this contract for delivery of sample office supplies.
                The agreement records the terms and conditions for the demonstration order.
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo-agreement.TXT", "text/plain", contract.getBytes(StandardCharsets.UTF_8));
        assertContractCompletes(file);
    }

    @ParameterizedTest
    @CsvSource({
            "demo-agreement.pdf, application/pdf",
            "demo-agreement.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    })
    void processesTextInBinaryDocuments(String name, String contentType) throws Exception {
        try (var content = new ClassPathResource(name).getInputStream()) {
            assertContractCompletes(new MockMultipartFile("file", name, contentType, content));
        }
    }

    @Test
    void rejectsUnsupportedUploadBeforeAddingItToTheQueue() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "unsupported.zip", "application/zip", new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(containsString("zip")));

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fileName").value(not(hasItem("unsupported.zip"))));
    }

    private void assertContractCompletes(MockMultipartFile file) throws Exception {
        String upload = mvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.content").doesNotHaveJsonPath())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(upload).get("id").asText();

        mvc.perform(post("/api/documents/{id}/process", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.category").value("CONTRACT"))
                .andExpect(jsonPath("$.content").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.contentText").value(containsString("Demo Supply Agreement")));

        mvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.content").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.contentText").value(containsString("Demo Supply Agreement")));

        for (String path : new String[] {"/api/documents", "/api/documents/status/COMPLETED",
                "/api/documents/category/CONTRACT"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(hasItem(id)))
                    .andExpect(jsonPath("$[*].content").isEmpty())
                    .andExpect(jsonPath("$[*].contentText").isEmpty());
        }
    }
}
