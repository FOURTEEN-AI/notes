package de.fourteen.notes.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.fourteen.notes.application.PreviewService;
import de.fourteen.notes.domain.CheckState;
import de.fourteen.notes.domain.DemoStep;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The bar of a preview app. Outside a preview app everything here is 404. */
@RestController
@RequestMapping("/api/preview")
class PreviewController {

    record PreviewResponse(
            int number,
            String title,
            String state,
            String deployedSha,
            CheckState build,
            CheckState review,
            CheckState acceptance,
            boolean mergeable,
            List<DemoStepResponse> demo) {}

    /** Named like in the ```demo block, which the frontend shows as it is. */
    record DemoStepResponse(@JsonProperty("do") String action, String expect, String selector) {

        static DemoStepResponse of(DemoStep step) {
            return new DemoStepResponse(step.action(), step.expect(), step.selector());
        }
    }

    record Feedback(String text) {}

    private final PreviewService preview;

    PreviewController(PreviewService preview) {
        this.preview = preview;
    }

    @GetMapping
    PreviewResponse get() {
        PreviewService.Preview p = preview.get();
        return new PreviewResponse(
                p.number(),
                p.title(),
                p.state(),
                p.deployedSha(),
                p.build(),
                p.review(),
                p.acceptance(),
                p.mergeable(),
                p.demo().stream().map(DemoStepResponse::of).toList());
    }

    @PostMapping(path = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    void feedback(@RequestBody Feedback feedback) {
        preview.feedback(feedback.text());
    }

    @PostMapping("/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void merge() {
        preview.merge();
    }
}
