ALTER TABLE projects ADD last_updated BIGINT NULL;
ALTER TABLE projects ADD last_updated_at INT NULL;
ALTER TABLE projects ADD CONSTRAINT fk_projects_last_updated_at__id FOREIGN KEY (last_updated_at) REFERENCES requests(id) ON DELETE RESTRICT ON UPDATE RESTRICT;