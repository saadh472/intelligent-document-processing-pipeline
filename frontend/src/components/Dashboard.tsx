import React, { useState, useEffect } from 'react';
import {
  Box,
  AppBar,
  Toolbar,
  Typography,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  CssBaseline,
  Container,
  Grid,
  Paper,
  Alert,
  CircularProgress,
  Button,
  Badge,
  IconButton,
  Chip,
  Divider,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import QueueIcon from '@mui/icons-material/Queue';
import SyncIcon from '@mui/icons-material/Sync';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import DashboardIcon from '@mui/icons-material/Dashboard';
import RefreshIcon from '@mui/icons-material/Refresh';
import UploadArea from './UploadArea';
import PipelineVisualization from './PipelineVisualization';
import DocumentQueue from './DocumentQueue';
import ProcessingLog from './ProcessingLog';
import { documentService } from '../services/api';
import { Document, DocumentStatus } from '../types/document';

const drawerWidth = 280;

const Dashboard: React.FC = () => {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [selectedStatus, setSelectedStatus] = useState<string>('all');
  const [selectedDocument, setSelectedDocument] = useState<Document | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    loadDocuments();
    const interval = setInterval(loadDocuments, 5000); // Refresh every 5 seconds
    return () => clearInterval(interval);
  }, []);

  const loadDocuments = async (showLoading = false) => {
    if (showLoading) {
      setRefreshing(true);
    }
    setError(null);

    try {
      const docs = await documentService.getAllDocuments();
      setDocuments(docs);
      setError(null);
    } catch (error: any) {
      console.error('Error loading documents:', error);
      const errorMessage = error.message || 'Failed to load documents. Please refresh the page.';
      setError(errorMessage);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleUpload = async () => {
    await loadDocuments(true);
  };

  const handleStatusSelect = (status: string) => {
    setSelectedStatus(status);
    setSelectedDocument(null); // Clear selection when changing filter
  };

  const getStatusCount = (status: DocumentStatus | 'processing' | 'all') => {
    if (status === 'all') return documents.length;
    if (status === 'processing') {
      return documents.filter(doc =>
        doc.status === DocumentStatus.TOKENIZED ||
        doc.status === DocumentStatus.EXTRACTED ||
        doc.status === DocumentStatus.VALIDATED ||
        doc.status === DocumentStatus.CATEGORIZED
      ).length;
    }
    return documents.filter(doc => doc.status === status).length;
  };

  const filteredDocuments = selectedStatus === 'all'
    ? documents
    : selectedStatus === 'processing'
    ? documents.filter(doc =>
        doc.status === DocumentStatus.TOKENIZED ||
        doc.status === DocumentStatus.EXTRACTED ||
        doc.status === DocumentStatus.VALIDATED ||
        doc.status === DocumentStatus.CATEGORIZED
      )
    : documents.filter(doc => doc.status === selectedStatus);

  const menuItems = [
    { id: 'all', label: 'All Documents', icon: <DashboardIcon />, count: getStatusCount('all') },
    { id: DocumentStatus.UPLOADED, label: 'Queue', icon: <QueueIcon />, count: getStatusCount(DocumentStatus.UPLOADED) },
    { id: 'processing', label: 'Processing', icon: <SyncIcon />, count: getStatusCount('processing') },
    { id: DocumentStatus.COMPLETED, label: 'Completed', icon: <CheckCircleIcon />, count: getStatusCount(DocumentStatus.COMPLETED) },
    { id: DocumentStatus.ERROR, label: 'Errors', icon: <ErrorIcon />, count: getStatusCount(DocumentStatus.ERROR) },
  ];

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <CssBaseline />
      <AppBar
        position="fixed"
        sx={{
          zIndex: (theme) => theme.zIndex.drawer + 1,
          bgcolor: 'primary.main',
          boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.1)',
        }}
      >
        <Toolbar>
          <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 600, flexGrow: 1 }}>
            📄 Document Processing Pipeline
          </Typography>
          <IconButton
            color="inherit"
            onClick={() => loadDocuments(true)}
            disabled={refreshing}
            sx={{ mr: 1 }}
          >
            <RefreshIcon />
          </IconButton>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            borderRight: '1px solid',
            borderColor: 'divider',
            bgcolor: 'background.paper',
          },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: 'auto', pt: 2 }}>
          <List>
            {menuItems.map((item) => (
              <ListItem key={item.id} disablePadding sx={{ mb: 0.5, px: 1 }}>
                <ListItemButton
                  selected={selectedStatus === item.id}
                  onClick={() => handleStatusSelect(item.id)}
                  sx={{
                    borderRadius: 2,
                    '&.Mui-selected': {
                      bgcolor: 'primary.main',
                      color: 'white',
                      '&:hover': {
                        bgcolor: 'primary.dark',
                      },
                      '& .MuiListItemIcon-root': {
                        color: 'white',
                      },
                    },
                  }}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    {item.icon}
                  </ListItemIcon>
                  <ListItemText
                    primary={item.label}
                    primaryTypographyProps={{
                      fontWeight: selectedStatus === item.id ? 600 : 400,
                    }}
                  />
                  {item.count > 0 && (
                    <Chip
                      label={item.count}
                      size="small"
                      sx={{
                        ml: 1,
                        bgcolor: selectedStatus === item.id ? 'rgba(255,255,255,0.2)' : 'action.selected',
                        color: selectedStatus === item.id ? 'white' : 'text.primary',
                        fontWeight: 600,
                      }}
                    />
                  )}
                </ListItemButton>
              </ListItem>
            ))}
          </List>
          <Divider sx={{ my: 2 }} />
          <Box sx={{ px: 2, pb: 2 }}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              Quick Stats
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">Total</Typography>
                <Typography variant="body2" fontWeight={600}>{documents.length}</Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">Completed</Typography>
                <Typography variant="body2" fontWeight={600} color="success.main">
                  {getStatusCount(DocumentStatus.COMPLETED)}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">Errors</Typography>
                <Typography variant="body2" fontWeight={600} color="error.main">
                  {getStatusCount(DocumentStatus.ERROR)}
                </Typography>
              </Box>
            </Box>
          </Box>
        </Box>
      </Drawer>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          bgcolor: 'background.default',
          p: 3,
        }}
      >
        <Toolbar />
        <Container maxWidth="xl">
          {error && (
            <Alert
              severity="warning"
              sx={{ mb: 3, borderRadius: 2 }}
              action={
                <Button color="inherit" size="small" onClick={() => loadDocuments(true)}>
                  Retry
                </Button>
              }
            >
              {error}
            </Alert>
          )}
          {loading && documents.length === 0 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
              <CircularProgress size={48} />
            </Box>
          )}
          <Grid container spacing={3}>
            <Grid item xs={12}>
              <Paper sx={{ p: 3, borderRadius: 3, boxShadow: 2 }}>
                <UploadArea onUpload={handleUpload} />
              </Paper>
            </Grid>
            <Grid item xs={12}>
              <Paper sx={{ p: 3, borderRadius: 3, boxShadow: 2 }}>
                <PipelineVisualization
                  documents={filteredDocuments}
                  onDocumentSelect={setSelectedDocument}
                />
              </Paper>
            </Grid>
            <Grid item xs={12} md={6}>
              <Paper sx={{ p: 3, borderRadius: 3, boxShadow: 2, height: '100%' }}>
                <DocumentQueue
                  documents={filteredDocuments}
                  selectedDocument={selectedDocument}
                  onDocumentSelect={setSelectedDocument}
                />
              </Paper>
            </Grid>
            <Grid item xs={12} md={6}>
              <Paper sx={{ p: 3, borderRadius: 3, boxShadow: 2, height: '100%' }}>
                <ProcessingLog selectedDocument={selectedDocument} />
              </Paper>
            </Grid>
          </Grid>
        </Container>
      </Box>
    </Box>
  );
};

export default Dashboard;
