package cl.duoc.pedidos360.report.controller;

import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import cl.duoc.pedidos360.report.dto.ProductSalesDTO;
import cl.duoc.pedidos360.report.dto.ReportSummaryDTO;
import cl.duoc.pedidos360.report.dto.StockSnapshotDTO;
import cl.duoc.pedidos360.report.service.ReportQueryService;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('Admin','Operator')")
@RequiredArgsConstructor
public class ReportController {
    private final ReportQueryService reportQueryService;

    @GetMapping("/summary")
    public ReportSummaryDTO summary() {
        return reportQueryService.summary();
    }

    @GetMapping("/top-products")
    public List<ProductSalesDTO> topProducts(@RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return reportQueryService.topProducts(limit);
    }

    @GetMapping("/low-stock")
    public List<StockSnapshotDTO> lowStock() {
        return reportQueryService.lowStock();
    }
}
