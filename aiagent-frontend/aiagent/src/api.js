import axios from 'axios';

const API = axios.create({
    baseURL: 'http://localhost:8085/api', // Your Spring Boot URL
});

export const askCoach = async (userMessage) => {
    try {
        const response = await API.get('/coach/ask', {
            params: { message: userMessage }
        });
        return response.data;
    } catch (error) {
        console.error("API Error:", error);
        return "Sorry, I'm having trouble connecting to the gym right now.";
    }
};