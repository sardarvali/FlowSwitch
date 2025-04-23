// Import required packages
const express = require('express');
const cors = require('cors');
const nodemailer = require('nodemailer');
const mongoose = require('mongoose');
const dotenv = require('dotenv');
const PORT = process.env.PORT || 3001;

// Load environment variables
dotenv.config();

// Initialize Express app
const app = express();
app.use(cors());
app.use(express.json());

// MongoDB Connection
mongoose.connect(process.env.MONGODB_URI)
  .then(() => console.log('Connected to MongoDB'))
  .catch(err => console.error('MongoDB connection error:', err));

// Define OTP Schema
const otpSchema = new mongoose.Schema({
  email: { type: String, required: true },
  otp: { type: String, required: true },
  createdAt: { type: Date, default: Date.now, expires: 300 } // OTP expires after 5 minutes
});

const OTP = mongoose.model('OTP', otpSchema);

// Configure Nodemailer (Using App Password)
const transporter = nodemailer.createTransport({
  service: 'gmail',
  auth: {
    user: process.env.EMAIL_USER,
    pass: process.env.EMAIL_PASSWORD,
  },
});

transporter.verify(function(error, success) {
  if (error) {
    console.error('SMTP connection error:', error);
  } else {
    console.log('SMTP server is ready to take our messages');
  }
});

async function sendOTPEmail(email, otp) {
  console.log(`Preparing to send OTP ${otp} to ${email}`);

  const mailOptions = {
    from: process.env.EMAIL_USER,
    to: email,
    subject: 'Your OTP Code',
    text: `Your OTP code is: ${otp}. It will expire in 5 minutes.`,
    html: `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;">
        <h2 style="color: #333;">Authentication Code</h2>
        <p>Your one-time password (OTP) for login is:</p>
        <h1 style="font-size: 32px; letter-spacing: 5px; text-align: center; margin: 20px 0; padding: 10px; background-color: #f5f5f5; border-radius: 5px;">${otp}</h1>
        <p>This code will expire in 5 minutes.</p>
        <p>If you didn't request this code, please ignore this email.</p>
      </div>
    `
  };

  try {
    console.log('Sending email...');
    const info = await transporter.sendMail(mailOptions);
    console.log('Email sent:', info.response);
    return info;
  } catch (error) {
    console.error('Error in sendOTPEmail function:', error);
    throw new Error(`Failed to send email: ${error.message}`);
  }
}

// Generate a random 6-digit OTP
function generateOTP() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

// API Routes
// Generate OTP
app.post('/api/generate-otp', async (req, res) => {
  try {
    const { email } = req.body;
    
    console.log('Received OTP request for:', email);

    if (!email) {
      return res.status(400).json('Email is required');
    }

    // Delete any existing OTP for this email
    try {
      await OTP.deleteMany({ email });
      console.log('Deleted existing OTPs for:', email);
    } catch (dbError) {
      console.error('Error deleting existing OTPs:', dbError);
      throw new Error(`Database error: ${dbError.message}`);
    }

    // Generate new OTP
    const otp = generateOTP();
    console.log('Generated OTP for:', email);

    // Save OTP to database
    try {
      const newOTP = new OTP({
        email,
        otp
      });
      await newOTP.save();
      console.log('Saved OTP to database for:', email);
    } catch (dbError) {
      console.error('Error saving OTP to database:', dbError);
      throw new Error(`Database error: ${dbError.message}`);
    }

    // Send OTP via email
    try {
      await sendOTPEmail(email, otp);
      console.log('Email sent successfully to:', email);
    } catch (emailError) {
      console.error('Error sending email:', emailError);
      throw new Error(`Email error: ${emailError.message}`);
    }

    res.status(200).json('OTP sent to your email');
  } catch (error) {
    console.error('Error in generate-otp endpoint:', error);
    res.status(500).json(`Failed to generate OTP: ${error.message}`);
  }
});

// Verify OTP
app.post('/api/verify-otp', async (req, res) => {
  try {
    const { email, otp } = req.body;
    
    if (!email || !otp) {
      return res.status(400).json('Email and OTP are required');
    }
    
    // Find OTP in database
    const otpRecord = await OTP.findOne({ email, otp });
    
    if (!otpRecord) {
      return res.status(400).json('Invalid OTP');
    }
    
    // OTP is valid, delete it to prevent reuse
    await OTP.deleteOne({ _id: otpRecord._id });
    
    res.status(200).json('OTP verified');
  } catch (error) {
    console.error('Error verifying OTP:', error);
    res.status(500).json(`Failed to verify OTP: ${error.message}`);
  }
});

// Health check endpoint
app.get('/', (req, res) => {
  res.send('OTP Service API is running');
});

// Start server
app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});