# BrokerX Performance Testing and Monitoring Makefile

.PHONY: help setup-monitoring start-monitoring stop-monitoring load-test stress-test spike-test clean-results

# Default target
help:
	@echo "BrokerX Performance Testing & Monitoring Commands"
	@echo "=================================================="
	@echo "Monitoring:"
	@echo "  setup-monitoring    - Install and configure monitoring stack"
	@echo "  start-monitoring    - Start Prometheus, Grafana, Redis, NGINX"
	@echo "  stop-monitoring     - Stop monitoring services"
	@echo "  monitoring-status   - Check monitoring services status"
	@echo ""
	@echo "Load Testing:"
	@echo "  load-test          - Run standard load test (200 users)"
	@echo "  stress-test        - Run stress test (2000 users)"
	@echo "  spike-test         - Run spike test (1500 user spike)"
	@echo "  test-local         - Run all tests on local environment"
	@echo "  test-staging       - Run all tests on staging environment"
	@echo ""
	@echo "Utilities:"
	@echo "  clean-results      - Clean old test results"
	@echo "  health-check       - Check system health"
	@echo "  generate-report    - Generate performance summary report"

# Monitoring Setup
setup-monitoring:
	@echo "🔧 Setting up monitoring infrastructure..."
	docker network create brokerx-monitoring || true
	docker-compose -f docker-compose.monitoring.yml pull
	@echo "✅ Monitoring setup complete"

start-monitoring:
	@echo "🚀 Starting monitoring stack..."
	docker-compose -f docker-compose.monitoring.yml up -d
	@echo "⏳ Waiting for services to start..."
	@sleep 30
	@echo "✅ Monitoring stack is running"
	@echo "🔗 Grafana: http://localhost:3000 (admin/admin)"
	@echo "🔗 Prometheus: http://localhost:9090"

stop-monitoring:
	@echo "⏹️  Stopping monitoring stack..."
	docker-compose -f docker-compose.monitoring.yml down
	@echo "✅ Monitoring stack stopped"

monitoring-status:
	@echo "📊 Monitoring Services Status:"
	@docker-compose -f docker-compose.monitoring.yml ps

# Load Testing
load-test:
	@echo "🔄 Running load test..."
	cd testing/load-tests && k6 run --env BASE_URL=http://localhost brokerx-load-test.js

stress-test:
	@echo "⚡ Running stress test..."
	cd testing/load-tests && k6 run --env BASE_URL=http://localhost stress-test.js

spike-test:
	@echo "🌊 Running spike test..."
	cd testing/load-tests && k6 run --env BASE_URL=http://localhost spike-test.js

test-local:
	@echo "🧪 Running all tests on local environment..."
	$(MAKE) health-check
	$(MAKE) load-test
	@sleep 60  # Wait between tests
	$(MAKE) stress-test
	@sleep 60
	$(MAKE) spike-test
	$(MAKE) generate-report

test-staging:
	@echo "🧪 Running tests on staging environment..."
	cd testing/load-tests && ./run-load-tests.sh load staging
	cd testing/load-tests && ./run-load-tests.sh stress staging

# Utilities
health-check:
	@echo "🔍 Performing health check..."
	@curl -s http://localhost/api/health || echo "❌ Health check failed"
	@curl -s http://localhost:8081/actuator/health || echo "❌ Auth service health check failed"
	@curl -s http://localhost:8082/actuator/health || echo "❌ Client service health check failed"
	@curl -s http://localhost:8083/actuator/health || echo "❌ Order service health check failed"
	@curl -s http://localhost:8084/actuator/health || echo "❌ Wallet service health check failed"
	@curl -s http://localhost:8085/actuator/health || echo "❌ Matching service health check failed"
	@echo "✅ Health check complete"

clean-results:
	@echo "🧹 Cleaning old test results..."
	rm -rf testing/load-tests/results/*
	@echo "✅ Results cleaned"

generate-report:
	@echo "📊 Generating performance summary report..."
	@mkdir -p reports/$(shell date +%Y%m%d)
	@echo "# BrokerX Performance Report - $(shell date)" > reports/$(shell date +%Y%m%d)/summary.md
	@echo "## Test Execution Summary" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Date: $(shell date)" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Environment: Local Development" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Tests Executed: Load Test, Stress Test, Spike Test" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "## Key Metrics" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Peak Concurrent Users: 2000" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Test Duration: ~51 minutes total" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Services Tested: All 5 microservices + Gateway" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "## Recommendations" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Review Grafana dashboards for detailed metrics" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Monitor CPU/Memory usage during peak loads" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "- Verify auto-scaling triggers are properly configured" >> reports/$(shell date +%Y%m%d)/summary.md
	@echo "✅ Report generated: reports/$(shell date +%Y%m%d)/summary.md"

# Advanced targets
benchmark:
	@echo "🎯 Running benchmark suite..."
	$(MAKE) start-monitoring
	@sleep 30  # Wait for monitoring to stabilize
	$(MAKE) test-local
	@echo "📈 Benchmark complete - check Grafana dashboards"

continuous-monitoring:
	@echo "🔄 Starting continuous monitoring mode..."
	@while true; do \
		$(MAKE) health-check; \
		sleep 300; \
	done

# Development helpers
dev-setup: setup-monitoring start-monitoring
	@echo "🛠️  Development environment ready!"
	@echo "Next steps:"
	@echo "1. Start your microservices: make start"
	@echo "2. Run load tests: make load-test"
	@echo "3. Check Grafana: http://localhost:3000"

install-k6:
	@echo "📦 Installing k6..."
ifeq ($(OS),Windows_NT)
	@echo "Please install k6 using: choco install k6 or scoop install k6"
else
	curl https://github.com/grafana/k6/releases/download/v0.47.0/k6-v0.47.0-linux-amd64.tar.gz -L | tar xvz --strip-components 1
	sudo mv k6 /usr/local/bin/
	@echo "✅ k6 installed successfully"
endif

# Integration with existing targets
performance-test: start-monitoring load-test stress-test spike-test
	@echo "🎉 Performance testing suite completed!"

# Emergency procedures
emergency-stop:
	@echo "🚨 Emergency stop - killing all tests and monitoring..."
	@pkill -f k6 || true
	$(MAKE) stop-monitoring
	@echo "✅ Emergency stop complete"