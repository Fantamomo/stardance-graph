ALTER TABLE projects RENAME COLUMN "followerCount" TO follower_count;
ALTER TABLE projects RENAME COLUMN "devlogCount" TO devlog_count;
ALTER TABLE projects RENAME COLUMN "totalHours" TO total_hours;

ALTER TABLE projects ADD COLUMN post_count INT NULL;
ALTER TABLE projects ADD COLUMN is_hardware BOOLEAN NULL;