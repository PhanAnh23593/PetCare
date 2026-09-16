CREATE TABLE clinics (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 user_id BIGINT NOT NULL UNIQUE,
 name VARCHAR(120) NOT NULL, thumbnail_url VARCHAR(500), phone VARCHAR(15),
 address VARCHAR(255) NOT NULL, rating DOUBLE, latitude DOUBLE, longitude DOUBLE,
 description TEXT, open_time TIME(6), close_time TIME(6), status BOOLEAN, map_link TEXT NOT NULL
);
CREATE TABLE services (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 clinic_id BIGINT, name VARCHAR(255) NOT NULL, price DECIMAL(38,2),
 CONSTRAINT fk_service_clinic FOREIGN KEY (clinic_id) REFERENCES clinics(id)
);
CREATE TABLE appointments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 user_id BIGINT NOT NULL, clinic_id BIGINT NOT NULL,
 full_name VARCHAR(120) NOT NULL, phone VARCHAR(15) NOT NULL,
 booking_type VARCHAR(30) NOT NULL, home_address VARCHAR(255),
 pet_type VARCHAR(255) NOT NULL, pet_condition VARCHAR(1000), pet_quantity INT NOT NULL,
 appointment_date DATE NOT NULL, appointment_time TIME(6) NOT NULL,
 status VARCHAR(20) NOT NULL, is_notified BOOLEAN NOT NULL, reject_reason VARCHAR(255),
 CONSTRAINT fk_appointment_clinic FOREIGN KEY (clinic_id) REFERENCES clinics(id),
 INDEX idx_appointment_user (user_id),
 INDEX idx_appointment_clinic_status (clinic_id,status)
);
CREATE TABLE appointment_services (
 appointment_id BIGINT NOT NULL, service_id BIGINT NOT NULL,
 CONSTRAINT fk_booking_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
 CONSTRAINT fk_booking_service FOREIGN KEY (service_id) REFERENCES services(id)
);
CREATE TABLE clinic_activation_outbox (user_id BIGINT PRIMARY KEY, attempts INT NOT NULL);
