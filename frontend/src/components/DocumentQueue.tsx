import React from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Chip,
  Stack,
  Avatar,
  LinearProgress,
  IconButton,
  Tooltip,
} from '@mui/material';
import DescriptionIcon from '@mui/icons-material/Description';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ScheduleIcon from '@mui/icons-material/Schedule';
import CategoryIcon from '@mui/icons-material/Category';
import { Document, DocumentStatus, DocumentCategory } from '../types/document';

interface DocumentQueueProps {
  documents: Document[];
  selectedDocument: Document | null;
  onDocumentSelect: (doc: Document) => void;
}

const DocumentQueue: React.FC<DocumentQueueProps> = ({
  documents,
  selectedDocument,
  onDocumentSelect,
}) => {
  const getStatusColor = (status: DocumentStatus) => {
    switch (status) {
      case DocumentStatus.COMPLETED:
        return 'success';
      case DocumentStatus.ERROR:
        return 'error';
      case DocumentStatus.UPLOADED:
        return 'default';
      case DocumentStatus.TOKENIZED:
      case DocumentStatus.EXTRACTED:
      case DocumentStatus.VALIDATED:
      case DocumentStatus.CATEGORIZED:
        return 'info';
      default:
        return 'default';
    }
  };

  const getStatusIcon = (status: DocumentStatus) => {
    switch (status) {
      case DocumentStatus.COMPLETED:
        return <CheckCircleIcon fontSize="small" />;
      case DocumentStatus.ERROR:
        return <ErrorIcon fontSize="small" />;
      default:
        return <ScheduleIcon fontSize="small" />;
    }
  };

  const getCategoryColor = (category: DocumentCategory) => {
    switch (category) {
      case DocumentCategory.CONTRACT:
        return 'primary';
      case DocumentCategory.MOTION:
        return 'secondary';
      case DocumentCategory.BRIEF:
        return 'warning';
      case DocumentCategory.EVIDENCE:
        return 'info';
      case DocumentCategory.CORRESPONDENCE:
        return 'success';
      default:
        return 'default';
    }
  };

  const getProgressPercentage = (status: DocumentStatus) => {
    switch (status) {
      case DocumentStatus.UPLOADED:
        return 10;
      case DocumentStatus.TOKENIZED:
        return 30;
      case DocumentStatus.EXTRACTED:
        return 50;
      case DocumentStatus.VALIDATED:
        return 70;
      case DocumentStatus.CATEGORIZED:
        return 90;
      case DocumentStatus.COMPLETED:
        return 100;
      case DocumentStatus.ERROR:
        return 0;
      default:
        return 0;
    }
  };

  const formatFileSize = (bytes: number) => {
    if (!bytes) return 'Unknown';
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          Document Queue
        </Typography>
        <Chip
          label={`${documents.length} document${documents.length !== 1 ? 's' : ''}`}
          size="small"
          color="primary"
        />
      </Box>

      <Stack spacing={2}>
        {documents.length === 0 ? (
          <Card sx={{ textAlign: 'center', py: 4, bgcolor: 'grey.50' }}>
            <CardContent>
              <DescriptionIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
              <Typography variant="body1" color="text.secondary">
                No documents found
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                Upload a document to get started
              </Typography>
            </CardContent>
          </Card>
        ) : (
          documents.map((doc) => {
            const isSelected = selectedDocument?.id === doc.id;
            const progress = getProgressPercentage(doc.status);

            return (
              <Card
                key={doc.id}
                onClick={() => onDocumentSelect(doc)}
                sx={{
                  cursor: 'pointer',
                  border: isSelected ? 2 : 1,
                  borderColor: isSelected ? 'primary.main' : 'divider',
                  bgcolor: isSelected ? 'primary.50' : 'background.paper',
                  transition: 'all 0.2s',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: 4,
                    borderColor: 'primary.main',
                  },
                }}
              >
                <CardContent>
                  <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
                    <Avatar
                      sx={{
                        bgcolor: isSelected ? 'primary.main' : 'grey.300',
                        width: 48,
                        height: 48,
                      }}
                    >
                      <DescriptionIcon />
                    </Avatar>
                    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                        <Typography
                          variant="h6"
                          sx={{
                            fontWeight: 600,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            flex: 1,
                          }}
                        >
                          {doc.fileName}
                        </Typography>
                        <Chip
                          icon={getStatusIcon(doc.status)}
                          label={doc.status}
                          size="small"
                          color={getStatusColor(doc.status) as any}
                          sx={{ ml: 1 }}
                        />
                      </Box>

                      <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap' }}>
                        {doc.category && (
                          <Chip
                            icon={<CategoryIcon fontSize="small" />}
                            label={doc.category}
                            size="small"
                            color={getCategoryColor(doc.category) as any}
                          />
                        )}
                        <Chip
                          label={formatFileSize(doc.fileSize || 0)}
                          size="small"
                          variant="outlined"
                        />
                        <Chip
                          label={formatDate(doc.uploadedAt)}
                          size="small"
                          variant="outlined"
                        />
                      </Stack>

                      {doc.status !== DocumentStatus.COMPLETED && doc.status !== DocumentStatus.ERROR && (
                        <Box sx={{ mb: 1 }}>
                          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                            <Typography variant="caption" color="text.secondary">
                              Processing...
                            </Typography>
                            <Typography variant="caption" fontWeight={600} color="primary.main">
                              {progress}%
                            </Typography>
                          </Box>
                          <LinearProgress
                            variant="determinate"
                            value={progress}
                            sx={{
                              height: 6,
                              borderRadius: 3,
                              bgcolor: 'grey.200',
                            }}
                          />
                        </Box>
                      )}

                      {doc.errorMessage && (
                        <Box sx={{ mt: 1, p: 1, bgcolor: 'error.50', borderRadius: 1 }}>
                          <Typography variant="caption" color="error.main">
                            {doc.errorMessage}
                          </Typography>
                        </Box>
                      )}
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            );
          })
        )}
      </Stack>
    </Box>
  );
};

export default DocumentQueue;
