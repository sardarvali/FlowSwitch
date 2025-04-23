const functions = require('firebase-functions');
const nodemailer = require('nodemailer');

// Set up environment variables with Firebase
// Run this command: firebase functions:config:set gmail.email="your-email@gmail.com" gmail.password="your-app-password"

exports.sendOTPEmail = functions.https.onCall(async (data, context) => {
  const { email, otp } = data;
  
  if (!email || !otp) {
    throw new functions.https.HttpsError('invalid-argument', 'Email and OTP are required');
  }

  // Get credentials from environment variables
  const gmailEmail = functions.config().gmail.email;
  const gmailPassword = functions.config().gmail.password;
  
  // Create a transporter using environment variables
  const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
      user: gmailEmail,
      pass: gmailPassword
    }
  });

  const mailOptions = {
    from: `GAIL App <${gmailEmail}>`,
    to: email,
    subject: 'Your OTP for GAIL App Login',
    html: `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px;">
        <h2 style="color: #5B21B6;">GAIL App - Login OTP</h2>
        <p>Hello,</p>
        <p>Your One-Time Password (OTP) for login is:</p>
        <div style="background-color: #f4f4f4; padding: 10px; text-align: center; font-size: 24px; font-weight: bold; letter-spacing: 5px; margin: 20px 0;">
          ${otp}
        </div>
        <p>This OTP will expire shortly. Please do not share this with anyone.</p>
        <p>If you did not request this OTP, please ignore this email.</p>
        <p>Thank you,<br>GAIL Support Team</p>
      </div>
    `
  };

  try {
    await transporter.sendMail(mailOptions);
    return { success: true, message: 'OTP sent successfully' };
  } catch (error) {
    console.error('Error sending email:', error);
    throw new functions.https.HttpsError('internal', 'Failed to send OTP email');
  }
});