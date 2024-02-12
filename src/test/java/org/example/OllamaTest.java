package org.example;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Image;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OllamaTest {

    private static final String OLLAMA_WITH_ORCA_MINI_MODEL = "%s-orca-mini".formatted(OllamaImage.IMAGE);

    private static final OllamaContainer ollama;

    static {
        ollama = new OllamaContainer(OllamaDockerImageName.image());
        ollama.start();
        createImage(ollama, OLLAMA_WITH_ORCA_MINI_MODEL);
    }

    @Test
    void test() throws IOException, InterruptedException {
        var stdout = ollama.execInContainer("ollama", "list").getStdout();
        System.out.println(stdout);
        assertThat(stdout).contains("orca-mini");
    }

    static class OllamaContainer extends GenericContainer<OllamaContainer> {

        private static final String MODEL = "orca-mini";

        private final DockerImageName dockerImageName;

        OllamaContainer(DockerImageName image) {
            super(image);
            this.dockerImageName = image;
            withExposedPorts(11434);
            withImagePullPolicy(dockerImageName -> !dockerImageName.getVersionPart().endsWith(MODEL));
        }

        @Override
        protected void containerIsStarted(InspectContainerResponse containerInfo) {
            if (!this.dockerImageName.getVersionPart().endsWith(MODEL)) {
                try {
                    execInContainer("ollama", "pull", MODEL);
                }
                catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Error pulling orca-mini model", e);
                }
            }
        }

    }

    static void createImage(GenericContainer<?> container, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(container.getDockerImageName());
        if (!dockerImageName.equals(DockerImageName.parse(localImageName))) {
            DockerClient dockerClient = DockerClientFactory.instance().client();
            List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
            if (images.isEmpty()) {
                DockerImageName imageModel = DockerImageName.parse(localImageName);
                dockerClient.commitCmd(container.getContainerId())
                        .withRepository(imageModel.getUnversionedPart())
                        .withLabels(Collections.singletonMap("org.testcontainers.sessionId", ""))
                        .withTag(imageModel.getVersionPart())
                        .exec();
            }
        }
    }

    static class OllamaDockerImageName {

        private final String baseImage;

        private final String localImageName;

        OllamaDockerImageName(String baseImage, String localImageName) {
            this.baseImage = baseImage;
            this.localImageName = localImageName;
        }

        static DockerImageName image() {
            return new OllamaDockerImageName(OllamaImage.IMAGE, OLLAMA_WITH_ORCA_MINI_MODEL).resolve();
        }

        private DockerImageName resolve() {
            var dockerImageName = DockerImageName.parse(this.baseImage);
            var dockerClient = DockerClientFactory.instance().client();
            var images = dockerClient.listImagesCmd().withReferenceFilter(this.localImageName).exec();
            if (images.isEmpty()) {
                return dockerImageName;
            }
            return DockerImageName.parse(this.localImageName);
        }

    }

}
