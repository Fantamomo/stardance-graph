ALTER TABLE request_iterations RENAME COLUMN program_id TO program_iteration;

ALTER TABLE request_iterations RENAME COLUMN total_warnings TO total_non_success_responses;