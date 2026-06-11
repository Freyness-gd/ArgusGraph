-- Projects imported from SBOMs. Lives in embedded H2, NOT in the knowledge graph.
CREATE TABLE IF NOT EXISTS project (
  id         IDENTITY      PRIMARY KEY,
  name       VARCHAR(255)  NOT NULL,
  created_at TIMESTAMP     NOT NULL
);

CREATE TABLE IF NOT EXISTS project_dependency (
  project_id BIGINT        NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  purl       VARCHAR(1024) NOT NULL,
  PRIMARY KEY (project_id, purl)
);
