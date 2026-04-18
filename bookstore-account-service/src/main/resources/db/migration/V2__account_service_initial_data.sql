-- Initial seed data for account service
-- Table renamed from USER to APP_USER (USER is reserved in H2 2.x)

INSERT INTO APP_USER (USER_ID, FIRST_NAME, LAST_NAME, PASSWORD, USER_NAME, EMAIL) VALUES
('asdasdsa-6727-4229-a4ab-zxczxcxczxcc', 'Deepak', 'Srivastav', '$2a$10$2XWkMz42.EApOBnx7nJaSupInwvBfPCGb5HZwWM.2RsC92joeAQzq', 'deepak.srivastav', 'deepak.srivastav@gmail.com'),
('xcvcvbvv-ba5d-4b92-85be-dfgdfgdfgdfg', 'Admin', 'Admin', '$2a$10$br7HrUzeQQ0ddR2ogg7L1.aRQ1sGC1rud.mL8VQBEKaMkx1G5zXR6', 'admin.admin', 'admin@gmail.com'),
('rertertr-6727-4229-a4ab-erererererer', 'Deepak', 'Srivastav', '$2a$10$2XWkMz42.EApOBnx7nJaSupInwvBfPCGb5HZwWM.2RsC92joeAQzq', 'deepak.dev', 'deepak.dev@gmail.com'),
('cvcvbcvb-ba5d-4b92-85be-fggfgtrytyty', 'Admin', 'Admin', '$2a$10$br7HrUzeQQ0ddR2ogg7L1.aRQ1sGC1rud.mL8VQBEKaMkx1G5zXR6', 'srivastav.deepak', 'srivastav.deepak@gmail.com'),
('cvbserte-6727-4229-a4ab-vbnbvvnvbnvb', 'Deepak', 'Srivastav', '$2a$10$2XWkMz42.EApOBnx7nJaSupInwvBfPCGb5HZwWM.2RsC92joeAQzq', 'deepak.sr', 'deepak.sr@gmail.com'),
('xcvxvcgv-ba5d-4b92-85be-fghfghtryfgh', 'Admin', 'Admin', '$2a$10$br7HrUzeQQ0ddR2ogg7L1.aRQ1sGC1rud.mL8VQBEKaMkx1G5zXR6', 'ds.admin', 'ds.admin@gmail.com'),
('ddfgdfgh-6727-4229-a4ab-ertdfgfdgdfg', 'Deepak', 'Srivastav', '$2a$10$2XWkMz42.EApOBnx7nJaSupInwvBfPCGb5HZwWM.2RsC92joeAQzq', 'srivastav.ds', 'srivastav.ds@gmail.com'),
('dfgdfgdf-ba5d-4b92-85be-vbvbvbnvbnjb', 'Admin', 'Admin', '$2a$10$br7HrUzeQQ0ddR2ogg7L1.aRQ1sGC1rud.mL8VQBEKaMkx1G5zXR6', 'ds.bro', 'ds.bro@gmail.com');

INSERT INTO ROLE (ROLE_ID, ROLE_NAME, ROLE_DESCRIPTION) VALUES ('9601409f-4691-4281-886e-8f8987763b56', 'STANDARD_USER', 'Standard User - Has no admin rights');
INSERT INTO ROLE (ROLE_ID, ROLE_NAME, ROLE_DESCRIPTION) VALUES ('f4b194d0-238b-41b5-8f18-630e5fcf3d8e', 'ADMIN_USER', 'Admin User - Has permission to perform admin tasks');
INSERT INTO ROLE (ROLE_ID, ROLE_NAME, ROLE_DESCRIPTION) VALUES ('erwerwer-erer-erfd-8f18-cvbdfgdgfggg', 'SELLER', 'Seller who can manage his inventory');
INSERT INTO ROLE (ROLE_ID, ROLE_NAME, ROLE_DESCRIPTION) VALUES ('tytryyrt-rtyr-rtyr-rtyr-fghfghfggfhg', 'PRODUCT_OWNER', 'Product Owner');

INSERT INTO USER_ROLES(USER_ID, ROLE_ID) VALUES('asdasdsa-6727-4229-a4ab-zxczxcxczxcc','9601409f-4691-4281-886e-8f8987763b56');
INSERT INTO USER_ROLES(USER_ID, ROLE_ID) VALUES('xcvcvbvv-ba5d-4b92-85be-dfgdfgdfgdfg','9601409f-4691-4281-886e-8f8987763b56');
INSERT INTO USER_ROLES(USER_ID, ROLE_ID) VALUES('xcvcvbvv-ba5d-4b92-85be-dfgdfgdfgdfg','f4b194d0-238b-41b5-8f18-630e5fcf3d8e');
INSERT INTO USER_ROLES(USER_ID, ROLE_ID) VALUES('rertertr-6727-4229-a4ab-erererererer','9601409f-4691-4281-886e-8f8987763b56');
INSERT INTO USER_ROLES(USER_ID, ROLE_ID) VALUES('dfgdfgdf-ba5d-4b92-85be-vbvbvbnvbnjb','tytryyrt-rtyr-rtyr-rtyr-fghfghfggfhg');
