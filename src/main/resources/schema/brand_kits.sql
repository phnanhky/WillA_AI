-- Chạy thủ công trên VPS nếu cần khôi phục nhanh:
-- docker exec -i willa-ai-postgres psql -U postgres -d willa_ai < brand_kits.sql

CREATE TABLE IF NOT EXISTS brand_kit_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL,
  title VARCHAR(200) NOT NULL,
  visual_dna_json TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  last_checked_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_brand_kit_profiles_user ON brand_kit_profiles(user_id);

CREATE TABLE IF NOT EXISTS brand_kit_reference_images (
  id BIGSERIAL PRIMARY KEY,
  profile_id BIGINT NOT NULL REFERENCES brand_kit_profiles(id) ON DELETE CASCADE,
  image_url TEXT NOT NULL,
  file_name VARCHAR(500),
  file_size_bytes BIGINT NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brand_kit_ref_images_profile ON brand_kit_reference_images(profile_id);

CREATE TABLE IF NOT EXISTS brand_kit_checks (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  profile_id BIGINT REFERENCES brand_kit_profiles(id) ON DELETE SET NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  avg_brand_score NUMERIC(5,1),
  total_assets INTEGER NOT NULL DEFAULT 0,
  report_json TEXT NOT NULL,
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brand_kit_checks_user ON brand_kit_checks(user_id);
CREATE INDEX IF NOT EXISTS idx_brand_kit_checks_profile ON brand_kit_checks(profile_id);

CREATE TABLE IF NOT EXISTS brand_kit_check_assets (
  id BIGSERIAL PRIMARY KEY,
  check_id BIGINT NOT NULL REFERENCES brand_kit_checks(id) ON DELETE CASCADE,
  image_url TEXT NOT NULL,
  file_name VARCHAR(500),
  brand_score NUMERIC(5,1),
  severity VARCHAR(20),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brand_kit_check_assets_check ON brand_kit_check_assets(check_id);
