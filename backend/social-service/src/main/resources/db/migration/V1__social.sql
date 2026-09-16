CREATE TABLE friendships (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 user_id BIGINT NOT NULL, friend_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
 CONSTRAINT unique_user_friend UNIQUE (user_id,friend_id),
 INDEX index_user_status (user_id,status), INDEX index_friend_status (friend_id,status)
);
CREATE TABLE pet_lockets (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 user_id BIGINT NOT NULL, image_url VARCHAR(1000) NOT NULL, caption VARCHAR(50),
 INDEX index_user_create_at (user_id,created_at DESC)
);
