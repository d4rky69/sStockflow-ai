export type ImportMode = 'VALIDATE_ONLY' | 'UPSERT';
export type ImportStatus = 'RUNNING' | 'VALIDATED' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'REJECTED' | 'FAILED';
export type ImportPackageType = 'SYNTHETIC_FOUNDATION' | 'SYNTHETIC_SALES';

export interface ImportJobView {
  importJobId: string;
  tenantId: string;
  importType: string;
  fileName: string;
  fileSha256: string;
  importMode: ImportMode;
  status: ImportStatus;
  startedAt: string;
  completedAt: string | null;
  totalRows: number;
  acceptedRows: number;
  rejectedRows: number;
  ignoredRows: number;
  message: string | null;
}

export interface ImportErrorView {
  importErrorId: string;
  fileName: string;
  rowNumber: number;
  errorCode: string;
  fieldName: string | null;
  rejectedValue: string | null;
  message: string;
}
