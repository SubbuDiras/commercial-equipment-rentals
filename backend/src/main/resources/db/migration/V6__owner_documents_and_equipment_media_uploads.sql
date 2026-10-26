ALTER TABLE owner_verification_documents
    MODIFY uploaded_at TIMESTAMP(6) NULL DEFAULT NULL,
    ADD COLUMN upload_expires_at TIMESTAMP(6) NULL;

ALTER TABLE equipment_images
    ADD COLUMN content_type VARCHAR(100) NULL,
    ADD COLUMN uploaded_at TIMESTAMP(6) NULL,
    ADD COLUMN upload_expires_at TIMESTAMP(6) NULL;

CREATE INDEX idx_owner_documents_profile ON owner_verification_documents (owner_profile_id, uploaded_at);
CREATE INDEX idx_equipment_images_listing ON equipment_images (equipment_id, uploaded_at, display_order);
