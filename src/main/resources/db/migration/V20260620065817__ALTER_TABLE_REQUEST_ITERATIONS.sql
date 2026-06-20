ALTER TABLE request_iterations ADD database_queries INT NULL;
ALTER TABLE request_iterations ADD database_cached INT NULL;
ALTER TABLE request_iterations ADD cache_hits INT NULL;
ALTER TABLE request_iterations ADD cache_misses INT NULL;
ALTER TABLE request_iterations ADD total_bytes_sent BIGINT NULL;
ALTER TABLE request_iterations ADD total_bytes_received BIGINT NULL;