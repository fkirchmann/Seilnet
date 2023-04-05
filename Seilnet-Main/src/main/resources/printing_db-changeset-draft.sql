--liquibase formatted sql

--changeset fkirchmann:5
CREATE TABLE Print_Printers (
	ID BIGINT NOT NULL AUTO_INCREMENT,
	Name VARCHAR(255) NOT NULL,
	Page_Cost DECIMAL(10,2) NOT NULL,
	Printer_Door_Open BOOLEAN NOT NULL DEFAULT FALSE,
	Paper_Door_Open BOOLEAN NOT NULL DEFAULT FALSE,
	Active_User_ID INTEGER,

	PRIMARY KEY (ID),
	FOREIGN KEY (Active_User_ID) REFERENCES Users(ID)
);

-- Events (params):
--  paper/printer_door_open/close, login, logout, wrong_pin (pin), pages_printed (pages), paper_empty,
--  balance_change(amount, reason, issuing_user_id)
CREATE TABLE Print_Events (
	ID INTEGER NOT NULL AUTO_INCREMENT,
	Event_Name VARCHAR(128) NOT NULL,
	Printer_ID BIGINT,
	User_ID INTEGER,
	Time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	Client_Info VARCHAR(255),

	-- Event-Specific parameters (NULL for any other event):
	-- wrong_pin
	Wrong_PIN VARCHAR(255),
	-- pages_printed
  Pages_Printed DECIMAL(10,0),
  -- balance_change
  Balance_Change_Amount DECIMAL(10,2),
  Balance_Change_Reason_I18n VARCHAR(255),
  Balance_Change_Issuing_User_ID INTEGER,

	PRIMARY KEY (ID),
	INDEX (User_ID),
	INDEX (Time),
	FOREIGN KEY (User_ID) REFERENCES Users(ID),
	FOREIGN KEY (Balance_Change_Issuing_User_ID) REFERENCES Users(ID)
);

CREATE TABLE Print_Jobs (
  -- Event ID is specified by PaperCut-NG
  Event_ID VARCHAR(255) NOT NULL,
	User_ID INTEGER,
	Printer_ID INTEGER NOT NULL,
  Document_Name VARCHAR(1024) NOT NULL,
  Pages DECIMAL(10,0) NOT NULL,
  Duplex BOOLEAN NOT NULL,
  Grayscale BOOLEAN NOT NULL,

  PRIMARY KEY (Event_ID),
	FOREIGN KEY (User_ID) REFERENCES Users(ID),
	FOREIGN KEY (Printer_ID) REFERENCES Printers(ID)
);

ALTER TABLE Users
  ADD COLUMN Print_Balance DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER Adblock,
	ADD COLUMN ID_Card_Barcode VARCHAR(255) AFTER Print_Balance,
	ADD COLUMN PIN VARCHAR(255) AFTER ID_Card_Barcode;

-- Settings:
--  PRINT_DEFAULT_BALANCE
CREATE TABLE Settings (
  Key VARCHAR(255) NOT NULL,
  --I18N_TAG VARCHAR(255), -- if NULL, the setting is only used internally (e.g. for persistent variables)
                           -- and not visible in the admin-UI
  Value MEDIUMTEXT,

  PRIMARY KEY (Key)
)