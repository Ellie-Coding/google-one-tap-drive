# Description
Demo project for the new Google OneTapSignIn linking it to an OAuth Google Drive request. 

This code combines the benefits of the more modern One-Tap-UI with the Google Drive REST interface. Note that authentication and authorization are currently handled sequentially right away. You can split that code up easily

# Working
- One-Tap-SignIn
- Legacy-SignIn
- Drive string file upload
- Drive string file download
- Choose between sign-in-methods

# To Do
- Error handling for user cancels and rejects
- Error handling for upload
- Error handling for Drive permission reject
- Error handling for IO Stream handling, string conversions, data corruption, aborts etc.
- Result handling of all internet calls. Only update UI when Drive has confirmed operation
