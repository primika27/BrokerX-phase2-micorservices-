# ============================================================================
# COMPREHENSIVE LOAD BALANCING AND CACHING ANALYSIS RUNNER - WINDOWS VERSION
# ============================================================================
# This script runs systematic tests across all instance configurations
# and generates comparative analysis for BrokerX microservices
# ============================================================================

param(
    [int[]]$InstanceCounts = @(1, 2, 3, 4),
    [string]$TestDuration = "8m",
    [int]$MaxVusBase = 50,
    [int]$CooldownBetweenTests = 30
)

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ResultsDir = Join-Path $ScriptDir "results\$(Get-Date -Format 'yyyyMMdd_HHmmss')"
$DockerComposeMonitoring = Join-Path $ProjectRoot "docker-compose.monitoring.yml"

# Create results directory
New-Item -ItemType Directory -Path $ResultsDir -Force | Out-Null

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================
function Write-LogInfo {
    param([string]$Message)
    Write-Host "ℹ️  $(Get-Date -Format 'HH:mm:ss') - $Message" -ForegroundColor Blue
}

function Write-LogSuccess {
    param([string]$Message)
    Write-Host "✅ $(Get-Date -Format 'HH:mm:ss') - $Message" -ForegroundColor Green
}

function Write-LogWarning {
    param([string]$Message)
    Write-Host "⚠️  $(Get-Date -Format 'HH:mm:ss') - $Message" -ForegroundColor Yellow
}

function Write-LogError {
    param([string]$Message)
    Write-Host "❌ $(Get-Date -Format 'HH:mm:ss') - $Message" -ForegroundColor Red
}

function Wait-ForSystemReady {
    param([int]$InstanceCount)
    
    Write-LogInfo "Waiting for system to be ready with $InstanceCount instances..."
    
    $maxAttempts = 30
    $attempt = 1
    
    while ($attempt -le $maxAttempts) {
        try {
            $response = Invoke-RestMethod -Uri "http://localhost:80/health" -Method Get -TimeoutSec 10
            Write-LogSuccess "System ready after $attempt attempts"
            return $true
        }
        catch {
            Write-LogInfo "Attempt $attempt/$maxAttempts - System not ready yet..."
            Start-Sleep -Seconds 5
            $attempt++
        }
    }
    
    Write-LogError "System failed to become ready after $maxAttempts attempts"
    return $false
}

function Test-Prerequisites {
    Write-LogInfo "Checking prerequisites..."
    
    # Check if k6 is installed
    try {
        $k6Version = k6 version
        Write-LogSuccess "k6 found: $($k6Version.Split("`n")[0])"
    }
    catch {
        Write-LogError "k6 is not installed. Please install k6 first."
        Write-LogInfo "Install k6: https://k6.io/docs/getting-started/installation/"
        exit 1
    }
    
    # Check if Docker is running
    try {
        docker info | Out-Null
        Write-LogSuccess "Docker is running"
    }
    catch {
        Write-LogError "Docker is not running. Please start Docker first."
        exit 1
    }
    
    # Check if monitoring stack configuration exists
    if (!(Test-Path $DockerComposeMonitoring)) {
        Write-LogError "Monitoring stack configuration not found: $DockerComposeMonitoring"
        exit 1
    }
    
    Write-LogSuccess "All prerequisites satisfied"
}

function Start-MonitoringStack {
    Write-LogInfo "Starting monitoring stack (Prometheus, Grafana, Redis)..."
    
    Set-Location $ProjectRoot
    docker-compose -f docker-compose.monitoring.yml up -d
    
    # Wait for monitoring to be ready
    Start-Sleep -Seconds 15
    
    # Verify monitoring services
    $services = @(
        @{Name="Prometheus"; Port=9090},
        @{Name="Grafana"; Port=3000},
        @{Name="Redis"; Port=6379}
    )
    
    foreach ($service in $services) {
        try {
            Invoke-RestMethod -Uri "http://localhost:$($service.Port)" -Method Get -TimeoutSec 5 | Out-Null
            Write-LogSuccess "$($service.Name) is ready"
        }
        catch {
            Write-LogWarning "$($service.Name) might not be ready yet (this is often normal)"
        }
    }
}

function Set-NginxConfiguration {
    param([int]$InstanceCount)
    
    Write-LogInfo "Configuring NGINX for $InstanceCount instances..."
    
    $nginxConfigFile = Join-Path $ProjectRoot "monitoring\nginx\nginx-${InstanceCount}instances.conf"
    $nginxTargetFile = Join-Path $ProjectRoot "monitoring\nginx\nginx.conf"
    
    if (!(Test-Path $nginxConfigFile)) {
        Write-LogError "NGINX configuration file not found: $nginxConfigFile"
        return $false
    }
    
    # Copy the appropriate configuration
    Copy-Item $nginxConfigFile $nginxTargetFile -Force
    
    # Restart NGINX container
    Set-Location $ProjectRoot
    docker-compose -f docker-compose.monitoring.yml restart nginx-lb
    Start-Sleep -Seconds 5
    
    Write-LogSuccess "NGINX configured for $InstanceCount instances"
    return $true
}

function Start-BrokerXInstances {
    param([int]$InstanceCount)
    
    Write-LogInfo "Starting BrokerX with $InstanceCount instances..."
    
    Set-Location $ProjectRoot
    
    # Stop existing services
    docker-compose -f docker-compose.yml down 2>$null | Out-Null
    docker-compose -f docker-compose.loadbalanced.yml down 2>$null | Out-Null
    
    # Start appropriate configuration
    $composeFile = switch ($InstanceCount) {
        1 { "docker-compose.1instance.yml" }
        2 { "docker-compose.2instances.yml" }
        3 { "docker-compose.3instances.yml" }
        4 { "docker-compose.4instances.yml" }
        default {
            Write-LogError "Unsupported instance count: $InstanceCount"
            return $false
        }
    }
    
    docker-compose -f $composeFile up -d
    
    # Wait for services to be ready
    return Wait-ForSystemReady $InstanceCount
}

function Invoke-LoadTest {
    param(
        [int]$InstanceCount,
        [bool]$CacheEnabled,
        [string]$TestName
    )
    
    Write-LogInfo "Running load test: $TestName"
    
    # Calculate max VUs based on instance count
    $maxVus = $MaxVusBase * $InstanceCount
    $resultFile = Join-Path $ResultsDir "${TestName}_results.json"
    
    # Prepare environment variables for k6
    $env:INSTANCES = $InstanceCount
    $env:CACHE_ENABLED = $CacheEnabled.ToString().ToLower()
    $env:BASE_URL = "http://localhost:80"
    $env:TEST_DURATION = $TestDuration
    $env:MAX_VUS = $maxVus
    
    # Run the test
    $testScript = Join-Path $ScriptDir "load-tests\comprehensive-loadbalancing-test.js"
    
    try {
        k6 run `
            --out "json=$resultFile" `
            --tag "instance_count=$InstanceCount" `
            --tag "cache_enabled=$($CacheEnabled.ToString().ToLower())" `
            --tag "test_name=$TestName" `
            $testScript
        
        Write-LogSuccess "Load test completed: $TestName"
        return $true
    }
    catch {
        Write-LogError "Load test failed: $TestName - $($_.Exception.Message)"
        return $false
    }
}

function Invoke-FaultToleranceTest {
    param([int]$InstanceCount)
    
    if ($InstanceCount -lt 2) {
        Write-LogInfo "Skipping fault tolerance test for single instance"
        return $true
    }
    
    Write-LogInfo "Running fault tolerance test for $InstanceCount instances..."
    
    $testName = "${InstanceCount}instances_fault_tolerance"
    $resultFile = Join-Path $ResultsDir "${testName}_results.json"
    $testScript = Join-Path $ScriptDir "load-tests\comprehensive-loadbalancing-test.js"
    
    # Prepare environment variables
    $env:INSTANCES = $InstanceCount
    $env:CACHE_ENABLED = "false"
    $env:BASE_URL = "http://localhost:80"
    $env:TEST_DURATION = "5m"
    $env:MAX_VUS = "30"
    
    # Start the load test in background
    $k6Job = Start-Job -ScriptBlock {
        param($TestScript, $ResultFile, $InstanceCount)
        k6 run `
            --out "json=$ResultFile" `
            --tag "test_type=fault_tolerance" `
            --tag "instance_count=$InstanceCount" `
            $TestScript
    } -ArgumentList $testScript, $resultFile, $InstanceCount
    
    Start-Sleep -Seconds 60  # Let the test stabilize
    
    # Simulate instance failure
    Write-LogInfo "Simulating instance failure..."
    Set-Location $ProjectRoot
    
    $composeFile = "docker-compose.${InstanceCount}instances.yml"
    $serviceName = switch ($InstanceCount) {
        2 { "gateway-service-2" }
        3 { "gateway-service-3" }
        4 { "gateway-service-4" }
    }
    
    docker-compose -f $composeFile stop $serviceName
    
    # Wait 60 seconds then restart
    Start-Sleep -Seconds 60
    
    Write-LogInfo "Restarting failed instance..."
    docker-compose -f $composeFile start $serviceName
    
    # Wait for k6 test to complete
    Wait-Job $k6Job | Out-Null
    $jobResult = Receive-Job $k6Job
    Remove-Job $k6Job
    
    Write-LogSuccess "Fault tolerance test completed"
    return $true
}

function New-SummaryReport {
    Write-LogInfo "Generating summary report..."
    
    $summaryFile = Join-Path $ResultsDir "test_summary.md"
    
    $content = @"
# BrokerX Load Balancing & Caching Analysis Results

**Test Execution Date**: $(Get-Date)  
**Test Duration per Configuration**: $TestDuration  
**Base Max VUs**: $MaxVusBase (scaled by instance count)

## Test Configurations Executed

"@
    
    foreach ($instanceCount in $InstanceCounts) {
        $content += @"

### $instanceCount Instance(s)

- **Without Caching**: Baseline performance measurement
- **With Caching**: Performance with Redis caching enabled
"@
        
        if ($instanceCount -gt 1) {
            $content += "`n- **Fault Tolerance**: Instance failure and recovery simulation"
        }
        
        $content += "`n"
    }
    
    $content += @"

## Result Files

The following JSON files contain detailed test results:

"@
    
    Get-ChildItem -Path $ResultsDir -Filter "*.json" | Sort-Object Name | ForEach-Object {
        $content += "- ``$($_.Name)```n"
    }
    
    $content += @"

## Grafana Dashboard

Access the comprehensive analysis dashboard at:
- **URL**: http://localhost:3000/d/brokerx-lb-cache-analysis
- **Username**: admin
- **Password**: brokerx123

## Next Steps

1. Import the result JSON files into your analysis tools
2. Review the Grafana dashboard for visual analysis
3. Compare performance metrics across instance counts
4. Analyze cache hit rates and performance improvements
5. Review fault tolerance behavior in multi-instance setups

"@
    
    Set-Content -Path $summaryFile -Value $content -Encoding UTF8
    
    Write-LogSuccess "Summary report generated: $summaryFile"
}

# ============================================================================
# MAIN EXECUTION FLOW
# ============================================================================
function Main {
    Write-Host "============================================================================" -ForegroundColor Cyan
    Write-Host "BrokerX Comprehensive Load Balancing & Caching Analysis" -ForegroundColor Cyan
    Write-Host "============================================================================" -ForegroundColor Cyan
    Write-Host ""
    
    # Check prerequisites
    Test-Prerequisites
    
    # Start monitoring stack
    Start-MonitoringStack
    
    # Run tests for each instance configuration
    foreach ($instanceCount in $InstanceCounts) {
        Write-LogInfo "Starting test suite for $instanceCount instances"
        
        # Configure load balancer
        if (!(Set-NginxConfiguration $instanceCount)) {
            Write-LogError "Failed to configure NGINX for $instanceCount instances"
            continue
        }
        
        # Start BrokerX instances
        if (!(Start-BrokerXInstances $instanceCount)) {
            Write-LogError "Failed to start BrokerX with $instanceCount instances"
            continue
        }
        
        # Test 1: Baseline (without caching)
        Write-LogInfo "Phase 1: Baseline performance (no cache)"
        $testName = "${instanceCount}instances_cache_false"
        Invoke-LoadTest -InstanceCount $instanceCount -CacheEnabled $false -TestName $testName
        
        # Cooldown
        Write-LogInfo "Cooldown period..."
        Start-Sleep -Seconds $CooldownBetweenTests
        
        # Test 2: With caching
        Write-LogInfo "Phase 2: Performance with caching"
        $testName = "${instanceCount}instances_cache_true"
        Invoke-LoadTest -InstanceCount $instanceCount -CacheEnabled $true -TestName $testName
        
        # Cooldown
        Start-Sleep -Seconds $CooldownBetweenTests
        
        # Test 3: Fault tolerance (only for multi-instance)
        Invoke-FaultToleranceTest -InstanceCount $instanceCount
        
        # Final cooldown
        Start-Sleep -Seconds $CooldownBetweenTests
        
        Write-LogSuccess "Completed test suite for $instanceCount instances"
        Write-Host ""
    }
    
    # Generate summary report
    New-SummaryReport
    
    Write-Host "============================================================================" -ForegroundColor Cyan
    Write-LogSuccess "All tests completed successfully!"
    Write-Host ""
    Write-LogInfo "Results directory: $ResultsDir"
    Write-LogInfo "Grafana dashboard: http://localhost:3000/d/brokerx-lb-cache-analysis"
    Write-LogInfo "Summary report: $(Join-Path $ResultsDir 'test_summary.md')"
    Write-Host "============================================================================" -ForegroundColor Cyan
}

# Execute main function
Main