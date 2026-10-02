# utils/alerts.py
import smtplib

def send_alert(message, to_email):
    """Send an email alert when trend change is detected."""
    server = smtplib.SMTP('smtp.gmail.com', 587)
    server.starttls()
    server.login("your_email", "your_password")  # ⚠️ Use environment variables in practice
    server.sendmail("your_email", to_email, message)
    server.quit()
