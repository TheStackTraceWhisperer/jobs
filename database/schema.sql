-- Database schema for the job platform
-- This script should be run after creating the database

USE jobs;
GO

-- Create the background_jobs table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'background_jobs')
BEGIN
    CREATE TABLE background_jobs (
        id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
        
        -- Discriminator for routing
        queue_name NVARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
        job_type NVARCHAR(255) NOT NULL,
        
        -- The Payload (JSON)
        payload NVARCHAR(MAX) NOT NULL,
        
        -- State Machine
        status NVARCHAR(20) NOT NULL DEFAULT 'QUEUED',
        attempts INT DEFAULT 0,
        
        -- Concurrency & Scheduling
        run_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        last_heartbeat DATETIME2 NULL,
        version INT DEFAULT 0,
        
        -- Tracing
        trace_id UNIQUEIDENTIFIER NULL,
        parent_job_id UNIQUEIDENTIFIER NULL,
        
        -- Audit
        created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
        last_error NVARCHAR(MAX) NULL,
        
        -- Priority (higher numbers = higher priority)
        priority INT NOT NULL DEFAULT 0
    );
END
GO

-- Critical Index for Polling Performance (includes priority for efficient polling)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_jobs_polling' AND object_id = OBJECT_ID('background_jobs'))
BEGIN
    CREATE INDEX idx_jobs_polling 
    ON background_jobs (status, queue_name, priority DESC, run_at ASC) 
    INCLUDE (version, attempts);
END
ELSE
BEGIN
    -- Drop and recreate index if it exists to update column order
    DROP INDEX idx_jobs_polling ON background_jobs;
    CREATE INDEX idx_jobs_polling 
    ON background_jobs (status, queue_name, priority DESC, run_at ASC) 
    INCLUDE (version, attempts);
END
GO

-- Index for Cleanup/Reaping
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_jobs_heartbeat' AND object_id = OBJECT_ID('background_jobs'))
BEGIN
    CREATE INDEX idx_jobs_heartbeat 
    ON background_jobs (status, last_heartbeat);
END
GO

PRINT 'Database schema created successfully';
