import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { COPILOT_API_BASE_URL } from '../config/api.config';

export interface CopilotChatRequest {
  conversationId: string;
  message: string;
  currentWorkspace?: string;
  selectedWarehouseId?: string;
  selectedSkuId?: string;
}

export interface CopilotEvidence {
  source: string;
  asOf: string;
  freshness: string;
  correlationId: string;
}

export interface CopilotChatResponse {
  answer: string;
  answerType: 'GROUNDED_EXPLANATION' | 'NO_DATA' | 'ERROR';
  confidence?: string;
  toolsUsed?: string[];
  evidence?: CopilotEvidence[];
  suggestedActions?: Record<string, unknown>[];
  warnings?: string[];
}

@Injectable({ providedIn: 'root' })
export class CopilotService {
  private readonly endpoint = `${COPILOT_API_BASE_URL}/api/v1/copilot/chat`;

  constructor(private readonly http: HttpClient) {}

  chat(request: CopilotChatRequest): Observable<CopilotChatResponse> {
    return this.http.post<CopilotChatResponse>(this.endpoint, request);
  }
}
