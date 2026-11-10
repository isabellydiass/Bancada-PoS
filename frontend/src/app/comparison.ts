export interface Comparison {
  id: string;
  createdAt: string;
  amountCents: number;
  firstDifference: number | null;
  baseline: FirmwareResult;
  candidate: FirmwareResult;
}

export interface FirmwareResult {
  firmware: string;
  verdict: 'PASS' | 'REGRESSION';
  authorizationIds: string[];
  steps: Step[];
}

export interface Step {
  order: number;
  code: string;
  description: string;
}
