package main.java.com.broker.marketDataService.controller;

import com.broker.marketDataService.dto.MarketQuote;
import com.broker.marketDataService.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    // Endpoint de streaming avec Server-Sent Events (alternative aux WebSockets)
    @GetMapping(value = "/quotes/stream", produces = "text/event-stream")
    public Flux<MarketQuote> streamQuotes(@RequestParam(required = false) String symbols) {
        Set<String> symbolSet = symbols != null ? Set.of(symbols.split(",")) : Set.of();
        
        return Flux.interval(Duration.ofSeconds(1))
            .flatMap(tick -> {
                List<MarketQuote> quotes = symbolSet.isEmpty() 
                    ? marketDataService.getAllLatestQuotes()
                    : marketDataService.getLatestQuotes(symbolSet);
                return Flux.fromIterable(quotes);
            });
    }

    // Health check
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("MarketData REST API is running"));
    }
}