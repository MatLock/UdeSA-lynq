// Read a File/Blob into a base64 data URL. Used to cache the uploaded profile
// image so it can be rendered without a (short-lived) pre-signed S3 URL.
const fileToDataUrl = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error ?? new Error('Failed to read file'));
    reader.readAsDataURL(file);
  });

export default fileToDataUrl;
