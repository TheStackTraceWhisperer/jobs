# Spring Cloud Bootstrap Support Implementation

## Overview
This implementation adds optional support for Spring Cloud Bootstrap to enable `@RefreshScope` on the job worker property configurations.

## Changes Made

### 1. Parent POM (`pom.xml`)
- Added Spring Cloud BOM version `2025.1.1` (compatible with Spring Boot 3.5.10)
- Imported Spring Cloud dependencies in dependency management

### 2. Worker Starter POM (`job-platform-worker-spring-boot-starter/pom.xml`)
- Added optional Spring Cloud dependencies:
  - `spring-cloud-context` - Provides @RefreshScope annotation
  - `spring-cloud-starter-bootstrap` - Enables bootstrap.properties support

### 3. Configuration Classes

#### SpringCloudRefreshScopeConfiguration
New configuration class that:
- Is conditionally activated when Spring Cloud Context is on the classpath (`@ConditionalOnClass`)
- Creates a `@Primary` bean of `JobWorkerProperties` with `@RefreshScope`
- Only activates when worker is enabled
- Allows dynamic property refresh without application restart

#### JobWorkerAutoConfiguration
- Updated to import `SpringCloudRefreshScopeConfiguration`
- Maintains backward compatibility for users without Spring Cloud

### 4. Documentation (`README.md`)
Added comprehensive section explaining:
- How to enable Spring Cloud Bootstrap
- Required dependencies
- Configuration steps
- How to trigger property refresh at runtime
- Note about optional nature of the feature

### 5. Reference Application Updates

#### bootstrap.properties
- Created example bootstrap configuration
- Includes comments for Spring Cloud Config Server integration
- Explains refresh endpoint usage

#### application.properties
- Added `refresh` endpoint to actuator exposure list

#### pom.xml
- Added commented-out Spring Cloud dependencies as example

## Key Design Decisions

### 1. Optional Dependencies
Spring Cloud dependencies are marked as `<optional>true</optional>` in the worker starter. This means:
- Users who don't need dynamic refresh don't get Spring Cloud in their dependencies
- Maintains backward compatibility
- No breaking changes for existing users

### 2. Conditional Bean Creation
Used `@ConditionalOnClass` with class name string to avoid compile-time dependency:
```java
@ConditionalOnClass(name = "org.springframework.cloud.context.config.annotation.RefreshScope")
```

This allows the configuration to compile without Spring Cloud on the classpath.

### 3. @Primary Bean
The `jobWorkerPropertiesRefreshScope()` bean is marked as `@Primary` to:
- Override the default bean created by `@EnableConfigurationProperties`
- Ensure the refresh-scoped version is used when Spring Cloud is available
- Provide seamless integration without user configuration

## Usage

### Without Spring Cloud (Default Behavior)
```xml
<!-- No additional dependencies needed -->
```
Properties are loaded normally but cannot be refreshed at runtime.

### With Spring Cloud (Dynamic Refresh Enabled)
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-context</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

Trigger refresh after property changes:
```bash
curl -X POST http://localhost:8080/actuator/refresh
```

## Testing
The implementation can be tested by:
1. Uncommenting Spring Cloud dependencies in reference-app
2. Starting the application
3. Changing properties in a config source
4. Calling the refresh endpoint
5. Verifying properties have updated without restart

## Compatibility
- Spring Boot: 3.5.10+
- Spring Cloud: 2025.1.1+
- Java: 21+
- Backward compatible with existing deployments
