package api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import projection.CategoryReportProjection;
import projection.CategoryTotal;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ReportController {

    private final CategoryReportProjection categories;

    public ReportController(CategoryReportProjection categories) {
        this.categories = categories;
    }

    @GetMapping("/accounts/{id}/reports/monthly")
    public List<CategoryTotal> monthly(@PathVariable UUID id, @RequestParam YearMonth month) {
        return categories.find(id, month);
    }

    @PostMapping("/admin/projections/categories/rebuild")
    public Map<String, Integer> rebuild() {
        return Map.of("eventsReplayed", categories.rebuild());
    }
}
