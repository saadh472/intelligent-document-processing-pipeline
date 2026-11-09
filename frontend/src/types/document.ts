export interface Document {
  id: string;
  fileName: string;
  fileType: string;
  mimeType: string;
  fileSize: number;
  status: DocumentStatus;
  category: DocumentCategory;
  uploadedAt: string;
  processedAt?: string;
  errorMessage?: string;
}

export enum DocumentStatus {
  UPLOADED = 'UPLOADED',
  TOKENIZED = 'TOKENIZED',
  EXTRACTED = 'EXTRACTED',
  VALIDATED = 'VALIDATED',
  CATEGORIZED = 'CATEGORIZED',
  COMPLETED = 'COMPLETED',
  ERROR = 'ERROR'
}

export enum DocumentCategory {
  CONTRACT = 'CONTRACT',
  MOTION = 'MOTION',
  BRIEF = 'BRIEF',
  EVIDENCE = 'EVIDENCE',
  CORRESPONDENCE = 'CORRESPONDENCE',
  OTHER = 'OTHER'
}

export interface ProcessingEvent {
  documentId: string;
  eventType: string;
  message: string;
  progressPercentage: number;
  timestamp: string;
  stage?: string;
  errorMessage?: string;
}
