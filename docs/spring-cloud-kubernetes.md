# Spring Cloud Kubernetes Configuration Management

This document explains how to use Spring Cloud Kubernetes to automatically detect ConfigMap and Secret changes in Kubernetes, and propagate those changes into the job platform application with automatic bean refresh.

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Setup and Dependencies](#setup-and-dependencies)
4. [ConfigMap Configuration](#configmap-configuration)
5. [Secret Configuration](#secret-configuration)
6. [Automatic Reload and Bean Refresh](#automatic-reload-and-bean-refresh)
7. [Kubernetes Deployment](#kubernetes-deployment)
8. [RBAC Permissions](#rbac-permissions)
9. [Testing Configuration Changes](#testing-configuration-changes)
10. [Troubleshooting](#troubleshooting)
11. [Best Practices](#best-practices)

## Overview

Spring Cloud Kubernetes provides integration between Spring Cloud applications and Kubernetes ConfigMaps and Secrets. It enables:

- **Automatic discovery** of ConfigMaps and Secrets
- **Real-time change detection** through Kubernetes watch API
- **Automatic configuration refresh** without pod restarts
- **Bean refresh** via `@RefreshScope` annotation
- **Seamless integration** with Spring Boot property sources

The job platform leverages Spring Cloud Kubernetes to allow dynamic reconfiguration of worker properties (concurrency, polling intervals, etc.) without downtime.

## Architecture

### How It Works

```
┌─────────────────┐
│   Kubernetes    │
│   API Server    │
└────────┬────────┘
         │ Watch API
         │ (ConfigMap/Secret changes)
         ▼
┌────────────────────────────┐
│ Spring Cloud Kubernetes    │
│ Config Watcher             │
└────────┬───────────────────┘
         │ Property Update
         ▼
┌────────────────────────────┐
│ Spring Environment         │
│ (Property Sources)         │
└────────┬───────────────────┘
         │ Refresh Event
         ▼
┌────────────────────────────┐
│ @RefreshScope Beans        │
│ (JobWorkerProperties)      │
└────────────────────────────┘
```

**Flow:**
1. Kubernetes API Server watches ConfigMap/Secret resources
2. When a change is detected, Spring Cloud Kubernetes receives the event
3. Configuration properties are updated in the Spring Environment
4. A `RefreshEvent` is published
5. All `@RefreshScope` beans are destroyed and recreated with new properties
6. The worker pool dynamically adjusts to new concurrency settings

## Setup and Dependencies

### 1. Add Maven Dependencies

Add the following dependencies to your application's `pom.xml`:

```xml
<dependencies>
    <!-- Spring Cloud Kubernetes Config -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-kubernetes-client-config</artifactId>
    </dependency>

    <!-- Spring Cloud Kubernetes Discovery (Optional) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-kubernetes-client</artifactId>
    </dependency>

    <!-- Spring Cloud Bootstrap Support -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-bootstrap</artifactId>
    </dependency>

    <!-- Spring Cloud Context (for @RefreshScope) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-context</artifactId>
    </dependency>
</dependencies>
```

### 2. Create bootstrap.yml

Spring Cloud Kubernetes requires bootstrap configuration to initialize before the main application context:

```yaml
# bootstrap.yml
spring:
  application:
    name: job-platform-worker
  cloud:
    kubernetes:
      enabled: true
      config:
        enabled: true
        # Watch for ConfigMap changes and reload
        enable-api: true
        sources:
          # Primary ConfigMap for application config
          - name: job-platform-config
            namespace: default
      secrets:
        enabled: true
        # Watch for Secret changes and reload
        enable-api: true
        sources:
          # Database credentials
          - name: job-platform-secrets
            namespace: default
      reload:
        enabled: true
        mode: event
        strategy: refresh
        monitoring-config-maps: true
        monitoring-secrets: true
```

**Key Configuration Parameters:**

- **`enable-api: true`**: Enables Kubernetes API access for reading ConfigMaps/Secrets
- **`reload.enabled: true`**: Enables automatic reload on changes
- **`reload.mode: event`**: Uses Kubernetes watch API (efficient, real-time)
- **`reload.strategy: refresh`**: Triggers Spring `RefreshScope` on changes
- **`monitoring-config-maps/secrets: true`**: Watches for changes to these resources

**Alternative Reload Modes:**

- **`event`** (Recommended): Uses Kubernetes watch API for real-time updates
- **`polling`**: Periodically polls ConfigMaps/Secrets (use if watch API is unavailable)

## ConfigMap Configuration

### Creating a ConfigMap

Create a ConfigMap containing your job platform worker properties:

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: job-platform-config
  namespace: default
data:
  application.yml: |
    platform:
      jobs:
        worker:
          enabled: true
          concurrency: 10
          queue-name: DEFAULT
          max-attempts: 3
          zombie-threshold-minutes: 5
          polling-interval-ms: 1000
          reaper-interval-ms: 60000
          queue-metrics-interval-ms: 15000
          shutdown-timeout-seconds: 30
    
    management:
      endpoints:
        web:
          exposure:
            include: health,metrics,prometheus,refresh
      metrics:
        export:
          prometheus:
            enabled: true
```

Apply the ConfigMap:

```bash
kubectl apply -f configmap.yaml
```

### ConfigMap Structure Options

Spring Cloud Kubernetes supports multiple ConfigMap formats:

#### Option 1: Single File (Recommended)
```yaml
data:
  application.yml: |
    platform.jobs.worker.concurrency: 10
```

#### Option 2: Key-Value Pairs
```yaml
data:
  platform.jobs.worker.concurrency: "10"
  platform.jobs.worker.queue-name: "DEFAULT"
```

#### Option 3: Multiple Files
```yaml
data:
  application.yml: |
    platform:
      jobs:
        worker:
          concurrency: 10
  datasource.yml: |
    spring:
      datasource:
        url: jdbc:sqlserver://...
```

## Secret Configuration

### Creating a Secret

Create a Secret for sensitive configuration like database credentials:

```yaml
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: job-platform-secrets
  namespace: default
type: Opaque
stringData:
  application.yml: |
    spring:
      datasource:
        url: jdbc:sqlserver://mssql:1433;databaseName=jobs;encrypt=false
        username: sa
        password: YourStrong!Passw0rd
        driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

Apply the Secret:

```bash
kubectl apply -f secret.yaml
```

**Important:** Use `stringData` for YAML content (automatically base64 encoded). Use `data` if you're providing pre-encoded values.

### Secret Best Practices

1. **Never commit secrets to source control**
2. **Use external secret management** (e.g., HashiCorp Vault, AWS Secrets Manager)
3. **Rotate secrets regularly**
4. **Limit secret access** with RBAC
5. **Use namespaced secrets** to avoid cross-contamination

## Automatic Reload and Bean Refresh

### How @RefreshScope Works

The job platform's `JobWorkerProperties` class is already configured with `@RefreshScope` support through `SpringCloudRefreshScopeConfiguration`:

```java
@Configuration
@ConditionalOnClass(name = "org.springframework.cloud.context.config.annotation.RefreshScope")
public class SpringCloudRefreshScopeConfiguration {
    
    @Bean
    @RefreshScope
    @Primary
    public JobWorkerProperties jobWorkerPropertiesRefreshScope() {
        return new JobWorkerProperties();
    }
}
```

**What Happens During Refresh:**

1. ConfigMap/Secret is updated in Kubernetes
2. Kubernetes watch API notifies Spring Cloud Kubernetes
3. Spring Environment is updated with new property values
4. `RefreshScopeRefreshedEvent` is published
5. All `@RefreshScope` beans are destroyed
6. Next access to these beans triggers recreation with new properties
7. Worker pool adjusts to new configuration automatically

### Refresh Behavior

- **Graceful shutdown**: Existing jobs complete before pool resizes
- **No downtime**: Application continues serving requests during refresh
- **Atomic updates**: All properties refresh together
- **Event-driven**: Only affected beans are recreated

### Manual Refresh Trigger

You can also manually trigger a refresh via the `/actuator/refresh` endpoint:

```bash
kubectl exec -it job-platform-worker-pod -- \
  curl -X POST http://localhost:8080/actuator/refresh
```

This is useful for testing or troubleshooting without modifying ConfigMaps.

## Kubernetes Deployment

### Complete Deployment Example

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: job-platform-worker
  namespace: default
spec:
  replicas: 3
  selector:
    matchLabels:
      app: job-platform-worker
  template:
    metadata:
      labels:
        app: job-platform-worker
    spec:
      serviceAccountName: job-platform-worker-sa
      containers:
      - name: worker
        image: your-registry/job-platform-worker:latest
        ports:
        - containerPort: 8080
          name: http
        env:
        # Optional: Override specific properties
        - name: SPRING_PROFILES_ACTIVE
          value: "worker"
        - name: SPRING_CLOUD_KUBERNETES_ENABLED
          value: "true"
        # Reference ConfigMap
        envFrom:
        - configMapRef:
            name: job-platform-config
        # Reference Secret
        - secretRef:
            name: job-platform-secrets
        # Liveness and Readiness Probes
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
---
apiVersion: v1
kind: Service
metadata:
  name: job-platform-worker
  namespace: default
spec:
  selector:
    app: job-platform-worker
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  type: ClusterIP
```

## RBAC Permissions

Spring Cloud Kubernetes requires specific Kubernetes RBAC permissions to watch ConfigMaps and Secrets.

### Create ServiceAccount

```yaml
# serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: job-platform-worker-sa
  namespace: default
```

### Create Role

```yaml
# role.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: job-platform-worker-role
  namespace: default
rules:
# Read ConfigMaps
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list", "watch"]
# Read Secrets
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list", "watch"]
# Read Services (for discovery)
- apiGroups: [""]
  resources: ["services", "endpoints"]
  verbs: ["get", "list", "watch"]
# Read Pods (for health checks)
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]
```

### Create RoleBinding

```yaml
# rolebinding.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: job-platform-worker-rolebinding
  namespace: default
subjects:
- kind: ServiceAccount
  name: job-platform-worker-sa
  namespace: default
roleRef:
  kind: Role
  name: job-platform-worker-role
  apiGroup: rbac.authorization.k8s.io
```

### Apply RBAC Resources

```bash
kubectl apply -f serviceaccount.yaml
kubectl apply -f role.yaml
kubectl apply -f rolebinding.yaml
```

**Security Note:** The `watch` verb is required for event-driven reload. If watch is not available in your cluster, use `polling` mode instead.

## Testing Configuration Changes

### Test 1: Update Worker Concurrency

1. **Check current concurrency:**
```bash
kubectl logs deployment/job-platform-worker | grep "concurrency"
```

2. **Update ConfigMap:**
```bash
kubectl edit configmap job-platform-config
# Change concurrency from 10 to 20
```

3. **Watch logs for reload event:**
```bash
kubectl logs -f deployment/job-platform-worker | grep -i "refresh\|reload"
```

Expected output:
```
INFO  RefreshEventListener - Received remote refresh request. Keys refreshed: [platform.jobs.worker.concurrency]
INFO  BackgroundWorker - Worker pool resizing: old=10, new=20
```

4. **Verify new concurrency:**
```bash
kubectl exec -it deployment/job-platform-worker -- \
  curl -s http://localhost:8080/actuator/metrics/jobs.worker.active | jq
```

### Test 2: Update Database Credentials

1. **Update Secret:**
```bash
# Create a temporary file (recommended for security)
cat > /tmp/secret-update.yaml <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: job-platform-secrets
  namespace: default
type: Opaque
stringData:
  application.yml: |
    spring:
      datasource:
        url: jdbc:sqlserver://mssql:1433;databaseName=jobs;encrypt=false
        username: sa
        password: NewPassword123!
        driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
EOF

kubectl apply -f /tmp/secret-update.yaml
rm -f /tmp/secret-update.yaml

# Alternative: Use kubectl edit for interactive editing
# kubectl edit secret job-platform-secrets
```

**Security Note:** Avoid using `--from-literal` for secrets as it exposes sensitive values in shell history and process lists. Use files or `kubectl edit` instead.

2. **Watch for refresh:**
```bash
kubectl logs -f deployment/job-platform-worker | grep -i "datasource\|refresh"
```

3. **Verify connection:**
```bash
kubectl exec -it deployment/job-platform-worker -- \
  curl -s http://localhost:8080/actuator/health | jq .components.db
```

Expected output:
```json
{
  "status": "UP",
  "details": {
    "database": "Microsoft SQL Server"
  }
}
```

### Test 3: Force Manual Refresh

If automatic reload isn't working, manually trigger refresh:

```bash
kubectl exec -it deployment/job-platform-worker -- \
  curl -X POST http://localhost:8080/actuator/refresh

# Response shows which properties were refreshed
["platform.jobs.worker.concurrency", "platform.jobs.worker.polling-interval-ms"]
```

## Troubleshooting

### Issue: ConfigMap Changes Not Detected

**Symptoms:**
- ConfigMap updated but application still uses old values
- No refresh events in logs

**Possible Causes:**

1. **Spring Cloud Kubernetes not enabled:**
```yaml
spring.cloud.kubernetes.enabled: false  # Should be true
```

2. **Reload mode incorrect:**
```yaml
spring.cloud.kubernetes.reload.mode: polling  # Should be 'event' for watch API
```

3. **RBAC permissions missing:**
```bash
# Verify ServiceAccount
kubectl get serviceaccount job-platform-worker-sa

# Verify Role includes 'watch'
kubectl describe role job-platform-worker-role

# Test permissions manually
kubectl auth can-i watch configmaps --as=system:serviceaccount:default:job-platform-worker-sa
```

4. **ConfigMap not in watched sources:**
```yaml
spring.cloud.kubernetes.config.sources:
  - name: wrong-configmap-name  # Should match actual ConfigMap
```

**Solution:**
- Enable debug logging:
```yaml
logging:
  level:
    org.springframework.cloud.kubernetes: DEBUG
```
- Check logs for initialization:
```bash
kubectl logs deployment/job-platform-worker | grep "Kubernetes"
```

### Issue: @RefreshScope Not Working

**Symptoms:**
- Refresh event fires but beans don't update

**Solution:**

1. Verify `@RefreshScope` bean is primary:
```bash
kubectl logs deployment/job-platform-worker | grep "jobWorkerPropertiesRefreshScope"
```

2. Check Spring Cloud Context dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-context</artifactId>
</dependency>
```

3. Ensure refresh endpoint is exposed:
```yaml
management.endpoints.web.exposure.include: refresh
```

### Issue: High Memory Usage After Multiple Refreshes

**Symptoms:**
- Memory grows after each ConfigMap update
- Old beans not garbage collected

**Solution:**

1. **Check for bean leaks:**
```bash
kubectl exec -it deployment/job-platform-worker -- \
  curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

2. **Review custom @RefreshScope beans:**
   - Ensure no static references
   - Avoid caching in @RefreshScope beans
   - Clean up resources in `@PreDestroy` methods

3. **Monitor refresh events:**
```bash
kubectl logs deployment/job-platform-worker | grep "RefreshScope" | wc -l
```

### Issue: Slow Refresh Performance

**Symptoms:**
- ConfigMap updates take 30+ seconds to propagate

**Solution:**

1. **Switch to event mode (watch API):**
```yaml
spring.cloud.kubernetes.reload.mode: event  # Not 'polling'
```

2. **Adjust watch timeout:**
```yaml
spring.cloud.kubernetes.reload.period: 5000  # Polling interval (ms) if using polling
```

3. **Check network latency:**
```bash
kubectl exec -it deployment/job-platform-worker -- \
  curl -w "@-" -o /dev/null -s https://kubernetes.default.svc/api/v1/namespaces/default/configmaps
```

## Best Practices

### 1. Use Separate ConfigMaps for Different Concerns

```yaml
# app-config.yaml - Application configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: job-platform-config
data:
  application.yml: |
    platform.jobs.worker: ...

---
# infra-config.yaml - Infrastructure configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: job-platform-infra
data:
  application.yml: |
    spring.datasource: ...
```

### 2. Version Your ConfigMaps

```yaml
metadata:
  name: job-platform-config-v2
  labels:
    version: v2
    app: job-platform-worker
```

This allows gradual rollout and easy rollback.

### 3. Use Immutable ConfigMaps for Stable Config

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: job-platform-config
immutable: true  # Cannot be changed; requires recreation
```

Use for configuration that should never change at runtime.

### 4. Monitor Configuration Changes

Add custom metrics for refresh events:

```java
@Component
public class ConfigRefreshListener {
    
    private final MeterRegistry registry;
    
    @EventListener
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        registry.counter("config.refresh.total", 
            "source", "kubernetes").increment();
    }
}
```

### 5. Test Configuration Validation

Add validation to properties to prevent invalid runtime updates:

```java
@ConfigurationProperties(prefix = "platform.jobs.worker")
@Validated
public class JobWorkerProperties {
    
    @Min(1)
    @Max(100)
    private int concurrency;
    
    @Min(100)
    private long pollingIntervalMs;
}
```

### 6. Document Configuration Schema

Maintain a schema document alongside ConfigMaps:

```yaml
# config-schema.yaml
schema:
  platform.jobs.worker.concurrency:
    type: integer
    range: [1, 100]
    default: 10
    description: "Number of concurrent job processing threads"
  platform.jobs.worker.polling-interval-ms:
    type: integer
    range: [100, 60000]
    default: 1000
    description: "Polling interval for checking new jobs (milliseconds)"
```

### 7. Use Health Checks for Configuration Validation

Add custom health indicators that fail when configuration is invalid:

```java
@Component
public class WorkerConfigHealthIndicator implements HealthIndicator {
    
    private final JobWorkerProperties properties;
    
    @Override
    public Health health() {
        if (properties.getConcurrency() < 1) {
            return Health.down()
                .withDetail("reason", "Invalid concurrency")
                .build();
        }
        return Health.up().build();
    }
}
```

### 8. Implement Graceful Degradation

Handle configuration errors gracefully:

```java
@Component
public class WorkerConfigRefreshListener {
    
    @EventListener
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        try {
            validateConfiguration();
            applyConfiguration();
        } catch (Exception e) {
            log.error("Configuration refresh failed, retaining previous config", e);
            // Keep using old configuration
        }
    }
}
```

## Additional Resources

- [Spring Cloud Kubernetes Documentation](https://spring.io/projects/spring-cloud-kubernetes)
- [Kubernetes ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Spring @RefreshScope](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#refresh-scope)
- [Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)

## Summary

Spring Cloud Kubernetes provides a powerful way to manage configuration in Kubernetes environments:

✅ **Real-time updates** without pod restarts  
✅ **Automatic detection** of ConfigMap and Secret changes  
✅ **Seamless integration** with Spring Boot configuration  
✅ **Bean refresh** via `@RefreshScope`  
✅ **Production-ready** with proper RBAC and monitoring  

The job platform is fully configured to leverage these capabilities, allowing dynamic reconfiguration of worker pools, database connections, and other critical settings in live production environments.
