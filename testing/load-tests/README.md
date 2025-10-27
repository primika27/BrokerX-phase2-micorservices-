# BrokerX Load Testing Suite

This directory contains comprehensive performance testing scripts for the BrokerX microservices platform using k6.

## 🎯 Test Types

### 1. Load Test (`brokerx-load-test.js`)
- **Purpose**: Simulate normal expected traffic patterns
- **Duration**: 17 minutes
- **Peak Load**: 200 concurrent users
- **Use Case**: Daily performance validation and capacity planning

### 2. Stress Test (`stress-test.js`)
- **Purpose**: Test system behavior under heavy load
- **Duration**: 17 minutes  
- **Peak Load**: 2000 concurrent users
- **Use Case**: Find system breaking points and resource limits

### 3. Spike Test (`spike-test.js`)
- **Purpose**: Test system resilience to sudden traffic spikes
- **Duration**: 8 minutes
- **Spike Load**: 1500 users in 10 seconds
- **Use Case**: Validate auto-scaling and circuit breaker patterns

## 📋 Prerequisites

1. **k6 Installation**
   ```bash
   # Windows (Chocolatey)
   choco install k6
   
   # Windows (Scoop)
   scoop install k6
   
   # Linux/Mac
   curl https://github.com/grafana/k6/releases/download/v0.47.0/k6-v0.47.0-linux-amd64.tar.gz -L | tar xvz --strip-components 1
   ```

2. **BrokerX System Running**
   - All microservices must be running
   - Monitoring stack should be active (Prometheus, Grafana)
   - Database connections established

3. **Test Data Setup**
   - Create test users in the system
   - Ensure market data is available
   - Verify authentication endpoints are accessible

## 🚀 Quick Start

### Windows
```cmd
# Run load test on local environment
run-load-tests.bat load local

# Run stress test on staging
run-load-tests.bat stress staging

# Run spike test on production (use with caution!)
run-load-tests.bat spike prod
```

### Linux/Mac
```bash
# Make script executable
chmod +x run-load-tests.sh

# Run load test on local environment
./run-load-tests.sh load local

# Run stress test on staging  
./run-load-tests.sh stress staging

# Run spike test on production (use with caution!)
./run-load-tests.sh spike prod
```

## 📊 Monitoring During Tests

### Key Metrics to Watch

1. **4 Golden Signals**
   - **Latency**: P95/P99 response times < 500ms
   - **Traffic**: Request rate (RPS)
   - **Errors**: Error rate < 5%
   - **Saturation**: CPU/Memory usage < 80%

2. **Custom Metrics**
   - Login duration
   - Order placement duration
   - Wallet check duration
   - System stability rate

### Grafana Dashboards
- **URL**: http://localhost:3000
- **Default Login**: admin/admin
- **Key Dashboards**:
  - BrokerX Performance Overview
  - Microservices Health
  - Load Testing Metrics
  - Infrastructure Resources

## 🎛️ Test Configuration

### Environment Variables
```bash
# Override base URL
export BASE_URL=https://api.brokerx.com

# Set test duration multiplier
export DURATION_MULTIPLIER=2

# Enable debug logging
export K6_LOG_LEVEL=debug
```

### Custom Test Parameters

Edit test files to modify:
- **User scenarios**: Modify `TEST_USERS` array
- **Stock symbols**: Update `STOCKS` array  
- **Request patterns**: Adjust probability weights
- **Thresholds**: Modify `options.thresholds`

## 📈 Results Analysis

### Output Files
Results are saved to `./results/YYYYMMDD_HHMMSS/`:
- `{test-type}-results.json`: Raw k6 metrics
- `summary.txt`: Aggregated performance summary
- `grafana-snapshot.png`: Dashboard screenshot (if configured)

### Key Metrics Interpretation

| Metric | Good | Warning | Critical |
|--------|------|---------|----------|
| P95 Response Time | < 200ms | 200-500ms | > 500ms |
| P99 Response Time | < 500ms | 500ms-1s | > 1s |
| Error Rate | < 1% | 1-5% | > 5% |
| Throughput | > 1000 RPS | 500-1000 RPS | < 500 RPS |

### Common Issues & Solutions

1. **High Error Rate**
   - Check database connections
   - Verify service health endpoints
   - Review application logs

2. **Slow Response Times**
   - Check resource utilization (CPU, memory)
   - Review database query performance
   - Examine network latency

3. **Failed Connections**
   - Verify load balancer configuration
   - Check service discovery
   - Review firewall settings

## 🔧 Advanced Configuration

### Custom Scenarios

Create new test scenarios by:
1. Copying existing test file
2. Modifying `options.stages`
3. Updating request patterns
4. Adding custom metrics

### Integration with CI/CD

```yaml
# Example GitHub Actions step
- name: Run Load Tests
  run: |
    ./run-load-tests.sh load staging
    if [ $? -ne 0 ]; then
      echo "Load tests failed!"
      exit 1
    fi
```

### Distributed Testing

For higher loads, use k6 cloud or distributed mode:
```bash
# Cloud execution
k6 cloud brokerx-load-test.js

# Distributed execution
k6 run --out influxdb=http://influxdb:8086/k6 brokerx-load-test.js
```

## 🚨 Safety Guidelines

### Production Testing
- **Schedule**: Run during low-traffic periods
- **Notification**: Alert operations team
- **Monitoring**: Have dashboards ready
- **Rollback**: Prepare quick rollback procedures

### Load Limits
- **Local**: Up to 500 concurrent users
- **Staging**: Up to 1000 concurrent users  
- **Production**: Start with 100, increase gradually

### Emergency Procedures
If system becomes unstable:
1. Stop the test immediately (`Ctrl+C`)
2. Check system health endpoints
3. Review error logs
4. Consider scaling down services
5. Alert incident response team

## 📚 Additional Resources

- [k6 Documentation](https://k6.io/docs/)
- [Performance Testing Best Practices](https://k6.io/docs/testing-guides/load-testing/)
- [BrokerX Architecture Documentation](../docs/arc42/docs.md)
- [Monitoring Setup Guide](../monitoring/README.md)

## 🤝 Contributing

To add new tests or improve existing ones:
1. Create feature branch
2. Add/modify test scripts
3. Update documentation
4. Test on staging environment
5. Submit pull request

## 📞 Support

For issues with load testing:
- Check logs in `./results/` directory
- Review Grafana dashboards
- Consult team documentation
- Contact DevOps team for infrastructure issues