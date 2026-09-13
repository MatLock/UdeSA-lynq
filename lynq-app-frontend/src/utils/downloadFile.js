const INVALID_FILE_NAME_CHARS = /[\\/:*?"<>|]+/g

const sanitizeFileName = (fileName) =>
  fileName.replace(INVALID_FILE_NAME_CHARS, '-').trim()

const triggerBlobDownload = (blob, fileName) => {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = sanitizeFileName(fileName)
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
}

const downloadFile = async (url, fileName) => {
  if (!url) {
    throw new Error('Nothing to download: the file has no URL')
  }

  const response = await fetch(url)
  if (!response.ok) {
    const error = new Error(`File download failed with status ${response.status}`)
    error.status = response.status
    throw error
  }

  triggerBlobDownload(await response.blob(), fileName)
}

export default downloadFile
