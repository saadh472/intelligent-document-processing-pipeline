import axios, { AxiosError, AxiosRequestConfig } from 'axios';
import { Document } from '../types/document';

export const API_BASE_URL = process.env.REACT_APP_API_URL || '/api';

export interface AuthSession {
  authenticated: boolean;
  username?: string;
  csrfToken: string;
}

let csrfToken = '';
const expiryListeners = new Set<() => void>();
export const onSessionExpired = (listener: () => void) => {
  expiryListeners.add(listener);
  return () => { expiryListeners.delete(listener); };
};
export const getCsrfToken = () => csrfToken;

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 120000, // 120 second timeout for large file operations
  withCredentials: true,
});

// Retry configuration
const RETRY_DELAY = 1000; // 1 second

// Helper function to check if error is retryable
const isRetryableError = (error: AxiosError): boolean => {
  if (!error.response) {
    // Network error or timeout - retry
    return true;
  }

  const status = error.response.status;
  // Retry on 5xx errors and 408 (timeout)
  return status >= 500 || status === 408;
};

// Add request interceptor for retry logic
api.interceptors.request.use(
  (config) => {
    if (!['get', 'head', 'options'].includes((config.method || 'get').toLowerCase()) && csrfToken) {
      config.headers.set('X-CSRF-TOKEN', csrfToken);
    }
    // Add timestamp to prevent caching (only for GET requests to avoid issues with POST/PUT)
    if (config.method === 'get' || config.method === 'GET') {
      config.params = {
        ...config.params,
        _t: Date.now(),
      };
    }

    // Remove Content-Type header for FormData to let axios set it automatically with boundary
    // Axios will automatically set the correct Content-Type when FormData is detected
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Add response interceptor to handle errors gracefully
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined;
    if (error.response?.status === 401 && originalRequest?.url !== '/auth/login') {
      csrfToken = '';
      expiryListeners.forEach(listener => listener());
    }
    if (error.response?.status === 403 && originalRequest?.url !== '/auth/session') {
      // Refresh the token for the next deliberate action. Never replay the denied write.
      try {
        const current = await authService.session();
        if (!current.authenticated) expiryListeners.forEach(listener => listener());
      } catch { /* Keep the original request error for the user. */ }
    }

    // Only retry reads. Retrying an upload or process request can duplicate work.
    if (originalRequest?.method?.toLowerCase() !== 'get' || originalRequest._retry || !isRetryableError(error)) {
      // Log error but don't break the app
      const errorData = error.response?.data;
      const errorMessage = (errorData && typeof errorData === 'object' && 'message' in errorData
        ? (errorData as { message: string }).message
        : null) || error.message || 'An error occurred';
      console.error('API Error:', errorMessage);
      return Promise.reject(error);
    }

    // Mark request as retried
    originalRequest._retry = true;

    // Wait before retry
    await new Promise(resolve => setTimeout(resolve, RETRY_DELAY));

    // Retry the request
    return api.request(originalRequest);
  }
);

const rememberSession = (session: AuthSession): AuthSession => {
  csrfToken = session.csrfToken;
  return session;
};

export const authService = {
  session: async (): Promise<AuthSession> => rememberSession((await api.get<AuthSession>('/auth/session', { timeout: 10000 })).data),
  login: async (username: string, password: string): Promise<AuthSession> => {
    // A fresh session request is required when the previous session expired.
    if (!csrfToken) await authService.session();
    const response = await api.post<AuthSession>('/auth/login', { username, password }, { timeout: 15000 });
    return rememberSession(response.data);
  },
  logout: async (): Promise<AuthSession> => rememberSession((await api.post<AuthSession>('/auth/logout', {}, { timeout: 10000 })).data),
};

export const documentService = {
  uploadDocument: async (file: File, onProgress?: (progress: number) => void): Promise<Document> => {
    const formData = new FormData();
    formData.append('file', file);

    // Log upload attempt for debugging
    console.log(`Uploading file: ${file.name}, size: ${(file.size / 1024 / 1024).toFixed(2)}MB, type: ${file.type}`);

    try {
      // Don't set Content-Type header - axios will automatically set it with boundary for FormData
      const response = await api.post<Document>('/documents/upload', formData, {
        timeout: 120000, // 120 seconds for file uploads (for large PDFs)
        maxContentLength: Infinity,
        maxBodyLength: Infinity,
        onUploadProgress: (progressEvent) => {
          if (onProgress && progressEvent.total) {
            const percentCompleted = Math.round(
              (progressEvent.loaded * 100) / progressEvent.total
            );
            onProgress(percentCompleted);
          }
        },
      });

      console.log('Upload successful:', response.data);
      return response.data;
    } catch (error: any) {
      console.error('Upload error details:', {
        message: error.message,
        code: error.code,
        response: error.response?.data,
        status: error.response?.status,
        fileName: file.name,
        fileSize: file.size,
        fileType: file.type
      });

      const errorData = error.response?.data;
      let errorMessage = 'Failed to upload document. Please try again.';

      if (error.response) {
        // Server responded with error
        if (errorData && typeof errorData === 'object' && 'message' in errorData) {
          errorMessage = (errorData as { message: string }).message;
        } else if (error.response.status === 413) {
          errorMessage = `File too large. Maximum size is 50MB. Your file is ${(file.size / 1024 / 1024).toFixed(2)}MB`;
        } else {
          errorMessage = `Server error (${error.response.status}): ${error.response.statusText}`;
        }
      } else if (error.request) {
        // Request was made but no response
        if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
          errorMessage = `Upload timeout after 120 seconds. The file (${(file.size / 1024 / 1024).toFixed(2)}MB) might be too large.`;
        } else if (error.code === 'ERR_NETWORK' || error.code === 'ECONNREFUSED') {
          errorMessage = 'Cannot connect to backend server. Please ensure the backend is running on http://localhost:8080';
        } else {
          errorMessage = `Network error: ${error.message || 'Please check your connection and ensure the backend is running'}`;
        }
      } else {
        errorMessage = error.message || errorMessage;
      }

      throw new Error(errorMessage);
    }
  },

  getAllDocuments: async (): Promise<Document[]> => {
    const response = await api.get<Document[]>('/documents', { timeout: 5000 });
    return response.data;
  },

  getDocumentById: async (id: string): Promise<Document> => {
    try {
      const response = await api.get<Document>(`/documents/${id}`);
      return response.data;
    } catch (error: any) {
      const errorData = error.response?.data;
      const errorMessage = (errorData && typeof errorData === 'object' && 'message' in errorData
        ? (errorData as { message: string }).message
        : null) || error.message || 'Failed to fetch document';
      throw new Error(errorMessage);
    }
  },

  getDocumentsByStatus: async (status: string): Promise<Document[]> => {
    try {
      const response = await api.get<Document[]>(`/documents/status/${status}`);
      return response.data || [];
    } catch (error: any) {
      console.error('Error fetching documents by status:', error);
      return [];
    }
  },

  getDocumentsByCategory: async (category: string): Promise<Document[]> => {
    try {
      const response = await api.get<Document[]>(`/documents/category/${category}`);
      return response.data || [];
    } catch (error: any) {
      console.error('Error fetching documents by category:', error);
      return [];
    }
  },

  processDocument: async (id: string): Promise<Document> => {
    try {
      const response = await api.post<Document>(`/documents/${id}/process`);
      return response.data;
    } catch (error: any) {
      const errorData = error.response?.data;
      const errorMessage = (errorData && typeof errorData === 'object' && 'message' in errorData
        ? (errorData as { message: string }).message
        : null) || error.message || 'Failed to process document';
      throw new Error(errorMessage);
    }
  },
};

export default api;
