CREATE TABLE INT_CHANNEL_MESSAGE (
	MESSAGE_ID CHAR(36) NOT NULL,
	GROUP_KEY CHAR(36) NOT NULL,
	CREATED_DATE TIMESTAMP NOT NULL,
	MESSAGE_BYTES BLOB,
	REGION VARCHAR(100) NOT NULL,
	constraint INT_CHANNEL_MESSAGE_PK primary key (GROUP_KEY, MESSAGE_ID, REGION)
);

CREATE INDEX INT_CHANNEL_MSG_DATE_IDX ON INT_CHANNEL_MESSAGE (CREATED_DATE);