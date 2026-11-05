-- Profile photo for any user (renter, owner, admin). The image is in private object storage; this is its key.
ALTER TABLE users
    ADD COLUMN profile_photo_key VARCHAR(255) NULL;
