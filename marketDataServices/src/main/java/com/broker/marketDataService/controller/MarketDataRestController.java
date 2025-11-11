package com.broker.marketDataService.controller;

import com.broker.marketDataService.dto.MarketQuote;
import com.broker.marketDataService.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/market-data")
@CrossOrigin(origins = "*")
public class MarketDataRestController {

    @Autowired
    private MarketDataService marketDataService;

    // Endpoint pour obtenir les dernières cotations
    @GetMapping("/quotes")
    public ResponseEntity<List<MarketQuote>> getQuotes(@RequestParam(required = false) String symbols) {
        try {
            List<MarketQuote> quotes;
            if (symbols != null && !symbols.isEmpty()) {
                Set<String> symbolSet = Set.of(symbols.split(","));
                quotes = marketDataService.getLatestQuotes(symbolSet);
            } else {
                quotes = marketDataService.getAllLatestQuotes();
            }
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }


}