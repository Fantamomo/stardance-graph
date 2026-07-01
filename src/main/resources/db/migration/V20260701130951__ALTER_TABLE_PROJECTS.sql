ALTER TABLE projects ADD last_seen INT;
-- noinspection SqlWithoutWhere
UPDATE projects SET last_seen = last_requested;
ALTER TABLE projects ALTER COLUMN last_seen SET NOT NULL;
ALTER TABLE projects ADD CONSTRAINT fk_projects_last_seen__id FOREIGN KEY (last_seen) REFERENCES requests(id) ON DELETE RESTRICT ON UPDATE RESTRICT;