import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DescriptionIcon from '@mui/icons-material/Description';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    LinearProgress,
    Paper,
    Stack,
    Typography,
} from '@mui/material';
import React, { useCallback, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { documentService } from '../services/api';

interface UploadAreaProps {
  onUpload: () => void;
}

const UploadArea: React.FC<UploadAreaProps> = ({ onUpload }) => {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadedFileName, setUploadedFileName] = useState<string | null>(null);

  const onDrop = useCallback(async (acceptedFiles: File[]) => {
    if (acceptedFiles.length === 0) return;

    const file = acceptedFiles[0];

    // Validate file size (max 50MB)
    const maxSize = 50 * 1024 * 1024; // 50MB
    if (file.size > maxSize) {
      setError(`File size exceeds 50MB limit. Your file is ${(file.size / 1024 / 1024).toFixed(2)}MB`);
      return;
    }

    setUploading(true);
    setError(null);
    setSuccess(null);
    setUploadProgress(0);
    setUploadedFileName(file.name);

    try {
      await documentService.uploadDocument(file, (progress) => {
        setUploadProgress(progress);
      });
      setSuccess(`Document "${file.name}" uploaded successfully!`);
      setUploadProgress(100);

      // Call onUpload callback, but don't fail if it errors
      try {
        onUpload();
      } catch (callbackError) {
        console.warn('Error refreshing document list after upload:', callbackError);
        // Don't fail the upload if refresh fails
      }

      // Clear success message after 5 seconds
      setTimeout(() => {
        setSuccess(null);
        setUploadProgress(0);
        setUploadedFileName(null);
      }, 5000);
    } catch (err: any) {
      console.error('Upload error:', err);
      let errorMessage = 'Failed to upload document. Please check your connection and try again.';

      if (err.response) {
        // Server responded with error
        const errorData = err.response.data;
        if (errorData && typeof errorData === 'object' && 'message' in errorData) {
          errorMessage = (errorData as { message: string }).message;
        }
      } else if (err.request) {
        // Request was made but no response received
        if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
          errorMessage = 'Upload timeout. The file might be too large or the server is slow. Please try again.';
        } else if (err.message?.includes('Network Error') || err.code === 'ERR_NETWORK' || !err.response) {
          errorMessage = 'Network error: Cannot connect to backend server. Please ensure the backend is running on http://localhost:8080. Check the console for backend startup logs.';
        } else {
          errorMessage = err.message || errorMessage;
        }
      } else {
        // Error setting up the request
        errorMessage = err.message || errorMessage;
      }

      // Log full error details for debugging
      console.error('Full upload error details:', {
        message: err.message,
        code: err.code,
        response: err.response,
        request: err.request,
        config: err.config
      });

      setError(errorMessage);
      setUploadProgress(0);
      setUploadedFileName(null);
    } finally {
      setUploading(false);
    }
  }, [onUpload]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/pdf': ['.pdf'],
      'application/msword': ['.doc'],
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
      'text/plain': ['.txt'],
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/png': ['.png'],
    },
    maxFiles: 1,
    disabled: uploading,
  });

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom sx={{ fontWeight: 600, mb: 3 }}>
        Upload Document
      </Typography>
      <Box
        {...getRootProps()}
        sx={{
          border: '2px dashed',
          borderColor: isDragActive ? 'primary.main' : 'grey.300',
          borderRadius: 3,
          p: 6,
          textAlign: 'center',
          cursor: uploading ? 'not-allowed' : 'pointer',
          bgcolor: isDragActive ? 'primary.50' : 'background.paper',
          transition: 'all 0.3s ease',
          position: 'relative',
          overflow: 'hidden',
          '&:hover': {
            borderColor: uploading ? 'grey.300' : 'primary.main',
            bgcolor: uploading ? 'background.paper' : 'primary.50',
            transform: uploading ? 'none' : 'translateY(-2px)',
            boxShadow: uploading ? 'none' : '0 4px 12px rgba(0,0,0,0.1)',
          },
        }}
      >
        <input {...getInputProps()} />
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          {uploading ? (
            <>
              <CircularProgress size={64} thickness={4} />
              <Typography variant="h6" color="primary">
                Uploading...
              </Typography>
            </>
          ) : success ? (
            <>
              <CheckCircleIcon sx={{ fontSize: 64, color: 'success.main' }} />
              <Typography variant="h6" color="success.main">
                Upload Successful!
              </Typography>
              {uploadedFileName && (
                <Typography variant="body2" color="text.secondary">
                  {uploadedFileName}
                </Typography>
              )}
            </>
          ) : (
            <>
              <CloudUploadIcon
                sx={{
                  fontSize: 64,
                  color: isDragActive ? 'primary.main' : 'text.secondary',
                  transition: 'all 0.3s',
                }}
              />
              <Box>
                <Typography variant="h6" gutterBottom sx={{ fontWeight: 600 }}>
                  {isDragActive ? 'Drop your file here' : 'Drag and drop a document'}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  or click to browse
                </Typography>
                <Button
                  variant="contained"
                  size="large"
                  disabled={uploading}
                  sx={{ mt: 1 }}
                >
                  Select File
                </Button>
              </Box>
            </>
          )}
        </Box>
      </Box>

      {uploading && (
        <Box sx={{ mt: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Uploading...
            </Typography>
            <Typography variant="body2" fontWeight={600} color="primary.main">
              {uploadProgress}%
            </Typography>
          </Box>
          <LinearProgress
            variant="determinate"
            value={uploadProgress}
            sx={{
              height: 8,
              borderRadius: 4,
              bgcolor: 'grey.200',
            }}
          />
        </Box>
      )}

      {!uploading && !success && (
        <Paper
          sx={{
            mt: 3,
            p: 2,
            bgcolor: 'grey.50',
            borderRadius: 2,
          }}
        >
          <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <DescriptionIcon fontSize="small" color="action" />
              <Typography variant="body2" color="text.secondary">
                Supported: PDF, DOC, DOCX, TXT, JPG, PNG
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="body2" color="text.secondary">
                Max size: 50MB
              </Typography>
            </Box>
          </Stack>
        </Paper>
      )}

      {error && (
        <Alert
          severity="error"
          sx={{ mt: 3, borderRadius: 2 }}
          onClose={() => setError(null)}
        >
          <Typography variant="body2" fontWeight={600}>
            Upload Failed
          </Typography>
          <Typography variant="body2">
            {error}
          </Typography>
        </Alert>
      )}

      {success && (
        <Alert
          severity="success"
          sx={{ mt: 3, borderRadius: 2 }}
          onClose={() => setSuccess(null)}
        >
          <Typography variant="body2" fontWeight={600}>
            {success}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Open the Pipeline tab and select Start Pipeline to process this document.
          </Typography>
        </Alert>
      )}
    </Box>
  );
};

export default UploadArea;
